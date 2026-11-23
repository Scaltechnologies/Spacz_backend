-- History of bookings and money.
--
-- booking_status_history: one append-only row per booking status change (who, when, why), written in the
--   same transaction as the change itself (see com.spacz.studyhall.history).
-- payment_transactions:   the money ledger. One PAYMENT row when money is received (Razorpay capture or a
--   vendor-confirmed Pay-at-Hall collection) and one REFUND row when money has to go back. Failed or abandoned
--   payment attempts move no money and are not ledger rows; they stay visible in payments / payment_audit_events.

CREATE TABLE booking_status_history (
    id                 BIGSERIAL    PRIMARY KEY,
    booking_id         BIGINT       NOT NULL REFERENCES bookings (id),
    from_status        VARCHAR(20),
    to_status          VARCHAR(20)  NOT NULL,
    actor_type         VARCHAR(20)  NOT NULL,
    actor_id           BIGINT,
    payment_id         BIGINT,
    reason             VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_booking_history_booking ON booking_status_history (booking_id, created_at, id);

CREATE TABLE payment_transactions (
    id                     BIGSERIAL     PRIMARY KEY,
    transaction_reference  VARCHAR(20)   NOT NULL,
    type                   VARCHAR(20)   NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    payment_id             BIGINT        NOT NULL REFERENCES payments (id),
    booking_id             BIGINT        NOT NULL REFERENCES bookings (id),
    booking_reference      VARCHAR(20)   NOT NULL,
    user_id                BIGINT        NOT NULL,
    vendor_id              BIGINT        NOT NULL,
    study_hall_id          BIGINT        NOT NULL,
    payment_method         VARCHAR(20)   NOT NULL,
    amount                 NUMERIC(12,2) NOT NULL,
    currency               VARCHAR(3)    NOT NULL DEFAULT 'INR',
    gateway_provider       VARCHAR(30),
    gateway_order_id       VARCHAR(100),
    gateway_payment_id     VARCHAR(100),
    gateway_refund_id      VARCHAR(100),
    collected_by_vendor_id BIGINT,
    description            VARCHAR(500),
    occurred_at            TIMESTAMPTZ   NOT NULL,
    completed_at           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_txn_reference UNIQUE (transaction_reference),
    -- A payment receives money at most once and is refunded at most once (always in full).
    CONSTRAINT uk_txn_payment_type UNIQUE (payment_id, type),
    CONSTRAINT ck_txn_amount CHECK (amount > 0)
);
CREATE INDEX idx_txn_user ON payment_transactions (user_id, occurred_at DESC);
CREATE INDEX idx_txn_vendor ON payment_transactions (vendor_id, occurred_at DESC);
CREATE INDEX idx_txn_hall ON payment_transactions (study_hall_id, occurred_at DESC);
CREATE INDEX idx_txn_booking ON payment_transactions (booking_id);

-- ── Backfill ────────────────────────────────────────────────────────────────

-- Bookings that existed before history recording started get one row with their status at that point.
INSERT INTO booking_status_history (booking_id, from_status, to_status, actor_type, reason, created_at)
SELECT b.id, NULL, b.status, 'SYSTEM', 'Recorded when booking history was introduced', b.created_at
FROM bookings b;

-- Money already received by existing payments (SUCCESS, or captured and then refunded / being refunded).
INSERT INTO payment_transactions (transaction_reference, type, status, payment_id, booking_id, booking_reference,
                                  user_id, vendor_id, study_hall_id, payment_method, amount, currency,
                                  gateway_provider, gateway_order_id, gateway_payment_id, collected_by_vendor_id,
                                  description, occurred_at, completed_at)
SELECT 'TXN-P' || LPAD(CAST(p.id AS VARCHAR), 14, '0'), 'PAYMENT', 'SUCCESS', p.id, p.booking_id, p.booking_reference,
       p.user_id, p.vendor_id, p.study_hall_id, p.payment_method, p.final_amount, p.currency,
       a.gateway_provider, a.gateway_order_id, a.gateway_payment_id, v.confirmed_by_vendor_id,
       p.description, COALESCE(v.confirmed_at, p.updated_at), COALESCE(v.confirmed_at, p.updated_at)
FROM payments p
LEFT JOIN LATERAL (SELECT * FROM payment_attempts pa
                   WHERE pa.payment_id = p.id AND pa.status = 'SUCCESS'
                   ORDER BY pa.attempt_number DESC LIMIT 1) a ON TRUE
LEFT JOIN pay_at_hall_verifications v ON v.payment_id = p.id
WHERE p.status IN ('SUCCESS', 'REFUND_PENDING', 'REFUNDED');

INSERT INTO payment_transactions (transaction_reference, type, status, payment_id, booking_id, booking_reference,
                                  user_id, vendor_id, study_hall_id, payment_method, amount, currency,
                                  gateway_provider, gateway_payment_id, gateway_refund_id, description,
                                  occurred_at, completed_at)
SELECT 'TXN-R' || LPAD(CAST(p.id AS VARCHAR), 14, '0'), 'REFUND',
       CASE WHEN p.status = 'REFUNDED' THEN 'SUCCESS' ELSE 'PENDING' END,
       p.id, p.booking_id, p.booking_reference, p.user_id, p.vendor_id, p.study_hall_id, p.payment_method,
       p.final_amount, p.currency, 'RAZORPAY',
       (SELECT pa.gateway_payment_id FROM payment_attempts pa
        WHERE pa.payment_id = p.id AND pa.status = 'SUCCESS' ORDER BY pa.attempt_number DESC LIMIT 1),
       p.gateway_refund_id, p.failure_reason, p.updated_at, p.refunded_at
FROM payments p
WHERE p.status IN ('REFUND_PENDING', 'REFUNDED');
