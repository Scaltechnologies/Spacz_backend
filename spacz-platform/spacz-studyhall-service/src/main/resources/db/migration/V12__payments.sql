-- Booking payments: Razorpay online checkout and Pay at Hall (the student pays at the counter and the
-- vendor confirms). A booking becomes CONFIRMED only when its payment reaches SUCCESS, in the same
-- local transaction (see PaymentServiceImpl#finalizeSuccessCore).

-- Per-vendor admin kill switch for Pay at Hall (admin-service toggles it via /internal).
ALTER TABLE vendor_profiles ADD COLUMN pay_at_hall_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- One payment intent per booking payment flow. status is backend-confirmed only; SUCCESS is immutable.
CREATE TABLE payments (
    id                 BIGSERIAL     PRIMARY KEY,
    user_id            BIGINT        NOT NULL,
    booking_id         BIGINT        NOT NULL REFERENCES bookings (id),
    study_hall_id      BIGINT        NOT NULL,
    seat_id            BIGINT        NOT NULL,
    vendor_id          BIGINT        NOT NULL,
    booking_reference  VARCHAR(20)   NOT NULL,
    description        VARCHAR(255)  NOT NULL,
    final_amount       NUMERIC(12,2) NOT NULL,
    currency           VARCHAR(3)    NOT NULL DEFAULT 'INR',
    status             VARCHAR(40)   NOT NULL,
    payment_method     VARCHAR(20)   NOT NULL,
    failure_reason     VARCHAR(500),
    idempotency_key    VARCHAR(100),
    expires_at         TIMESTAMPTZ   NOT NULL,
    gateway_refund_id  VARCHAR(100),
    refunded_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_payments_amount CHECK (final_amount > 0)
);
CREATE INDEX idx_payments_user ON payments (user_id);
CREATE INDEX idx_payments_booking ON payments (booking_id);
CREATE INDEX idx_payments_status_expires ON payments (status, expires_at);
CREATE INDEX idx_payments_vendor_method ON payments (vendor_id, payment_method, status);
-- DB-level guarantees for the two most important invariants. The status list of the first index
-- must match PaymentStatus.OPEN: update both in the same change.
CREATE UNIQUE INDEX uq_payments_one_open_per_booking ON payments (booking_id)
    WHERE status IN ('CREATED', 'PENDING', 'PROCESSING', 'AWAITING_VENDOR_CONFIRMATION');
CREATE UNIQUE INDEX uq_payments_one_success_per_booking ON payments (booking_id)
    WHERE status = 'SUCCESS';

-- One gateway charge try (new Razorpay order per retry; never reused).
CREATE TABLE payment_attempts (
    id                  BIGSERIAL    PRIMARY KEY,
    payment_id          BIGINT       NOT NULL REFERENCES payments (id),
    attempt_number      INTEGER      NOT NULL,
    gateway_provider    VARCHAR(30)  NOT NULL DEFAULT 'RAZORPAY',
    gateway_order_id    VARCHAR(100) NOT NULL,
    gateway_payment_id  VARCHAR(100),
    status              VARCHAR(40)  NOT NULL,
    failure_code        VARCHAR(100),
    failure_reason      VARCHAR(500),
    signature_verified  BOOLEAN,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_attempt_gateway_order_id UNIQUE (gateway_order_id),
    CONSTRAINT uk_attempt_gateway_payment_id UNIQUE (gateway_payment_id),
    CONSTRAINT uk_attempt_number UNIQUE (payment_id, attempt_number)
);

-- Webhook dedupe log: the UNIQUE dedupe_key is what makes replays a no-op. Rows whose
-- processing_result is NULL or 'ERROR%' are reprocessed on the next delivery.
CREATE TABLE payment_webhook_events (
    id                  BIGSERIAL     PRIMARY KEY,
    dedupe_key          VARCHAR(200)  NOT NULL,
    event_type          VARCHAR(100)  NOT NULL,
    payment_attempt_id  BIGINT,
    raw_payload         TEXT,
    processing_result   VARCHAR(1000),
    received_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    processed_at        TIMESTAMPTZ,
    CONSTRAINT uk_webhook_dedupe_key UNIQUE (dedupe_key)
);

-- Append-only audit trail. Never stores raw QR tokens or codes.
CREATE TABLE payment_audit_events (
    id               BIGSERIAL    PRIMARY KEY,
    payment_id       BIGINT       NOT NULL REFERENCES payments (id),
    actor_type       VARCHAR(20)  NOT NULL,
    actor_id         BIGINT,
    action           VARCHAR(50)  NOT NULL,
    previous_status  VARCHAR(40),
    new_status       VARCHAR(40),
    metadata         VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_payaudit_payment_id ON payment_audit_events (payment_id);

-- Pay at Hall companion row: hashes only.
CREATE TABLE pay_at_hall_verifications (
    id                      BIGSERIAL    PRIMARY KEY,
    payment_id              BIGINT       NOT NULL REFERENCES payments (id),
    qr_token_hash           VARCHAR(128) NOT NULL,
    verification_code_hash  VARCHAR(128) NOT NULL,
    attempt_count           INTEGER      NOT NULL DEFAULT 0,
    locked_until            TIMESTAMPTZ,
    confirmation_method     VARCHAR(10),
    confirmed_by_vendor_id  BIGINT,
    confirmed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_pahv_payment_id UNIQUE (payment_id),
    CONSTRAINT uk_pahv_qr_token_hash UNIQUE (qr_token_hash)
);
