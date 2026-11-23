-- Partner wallet: what SPACZ owes each vendor, and the vendor's withdrawal (payout) requests.
--
-- vendor_wallet_entries:   append-only wallet ledger. The balance is the sum of amount (credits > 0, debits < 0).
--   BOOKING_EARNING      + vendor's share of an ONLINE payment SPACZ received (seat price - discount - commission)
--   BOOKING_REFUND       - that share reversed when the payment is refunded
--   PAY_AT_HALL_FEE      - platform charges + tax + commission of a Pay at Hall booking (the vendor kept all the cash)
--   WITHDRAWAL           - money reserved by a withdrawal request
--   WITHDRAWAL_REVERSAL  + that money released again (request rejected or cancelled)
--   An entry counts towards the available balance from available_at (earnings: after the settlement hold).
-- vendor_payout_accounts:  the vendor's UPI IDs and bank accounts (soft-deleted).
-- vendor_withdrawals:      withdrawal requests, with a snapshot of the payout account they are paid to.

CREATE TABLE vendor_payout_accounts (
    id                   BIGSERIAL     PRIMARY KEY,
    vendor_id            BIGINT        NOT NULL,
    method               VARCHAR(20)   NOT NULL,
    upi_id               VARCHAR(100),
    account_holder_name  VARCHAR(120),
    account_number       VARCHAR(20),
    ifsc_code            VARCHAR(11),
    bank_name            VARCHAR(100),
    is_default           BOOLEAN       NOT NULL DEFAULT FALSE,
    active               BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_payout_account_details CHECK (
        (method = 'UPI' AND upi_id IS NOT NULL)
        OR (method = 'BANK_ACCOUNT' AND account_number IS NOT NULL AND ifsc_code IS NOT NULL
            AND account_holder_name IS NOT NULL))
);
CREATE INDEX idx_payout_accounts_vendor ON vendor_payout_accounts (vendor_id, active);

CREATE TABLE vendor_withdrawals (
    id                     BIGSERIAL     PRIMARY KEY,
    reference              VARCHAR(20)   NOT NULL,
    vendor_id              BIGINT        NOT NULL,
    amount                 NUMERIC(12,2) NOT NULL,
    currency               VARCHAR(3)    NOT NULL DEFAULT 'INR',
    status                 VARCHAR(20)   NOT NULL,
    payout_account_id      BIGINT        NOT NULL REFERENCES vendor_payout_accounts (id),
    payout_method          VARCHAR(20)   NOT NULL,
    upi_id                 VARCHAR(100),
    account_holder_name    VARCHAR(120),
    account_number         VARCHAR(20),
    ifsc_code              VARCHAR(11),
    bank_name              VARCHAR(100),
    vendor_note            VARCHAR(255),
    admin_note             VARCHAR(500),
    rejection_reason       VARCHAR(500),
    payout_reference       VARCHAR(100),
    processed_by_admin_id  BIGINT,
    requested_at           TIMESTAMPTZ   NOT NULL,
    processing_at          TIMESTAMPTZ,
    paid_at                TIMESTAMPTZ,
    rejected_at            TIMESTAMPTZ,
    cancelled_at           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_withdrawal_reference UNIQUE (reference),
    CONSTRAINT ck_withdrawal_amount CHECK (amount > 0)
);
CREATE INDEX idx_withdrawals_vendor ON vendor_withdrawals (vendor_id, requested_at DESC);
CREATE INDEX idx_withdrawals_status ON vendor_withdrawals (status, requested_at);
-- At most one open request per vendor (also checked in code under the vendor row lock).
CREATE UNIQUE INDEX uq_withdrawals_one_open_per_vendor ON vendor_withdrawals (vendor_id)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE TABLE vendor_wallet_entries (
    id                 BIGSERIAL     PRIMARY KEY,
    vendor_id          BIGINT        NOT NULL,
    type               VARCHAR(30)   NOT NULL,
    amount             NUMERIC(12,2) NOT NULL,
    currency           VARCHAR(3)    NOT NULL DEFAULT 'INR',
    available_at       TIMESTAMPTZ   NOT NULL,
    payment_id         BIGINT        REFERENCES payments (id),
    booking_id         BIGINT        REFERENCES bookings (id),
    booking_reference  VARCHAR(20),
    withdrawal_id      BIGINT        REFERENCES vendor_withdrawals (id),
    description        VARCHAR(500),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Idempotency: one entry of each type per payment and per withdrawal.
    CONSTRAINT uk_wallet_entry_payment UNIQUE (payment_id, type),
    CONSTRAINT uk_wallet_entry_withdrawal UNIQUE (withdrawal_id, type),
    CONSTRAINT ck_wallet_entry_amount CHECK (amount <> 0)
);
CREATE INDEX idx_wallet_entries_vendor ON vendor_wallet_entries (vendor_id, created_at DESC, id DESC);
CREATE INDEX idx_wallet_entries_available ON vendor_wallet_entries (vendor_id, available_at);

-- ── Backfill ────────────────────────────────────────────────────────────────
-- Online payments already received (no commission existed before this migration). Settled immediately:
-- these payments are older than the settlement hold anyway, or close to it.
INSERT INTO vendor_wallet_entries (vendor_id, type, amount, currency, available_at, payment_id, booking_id,
                                   booking_reference, description, created_at)
SELECT p.vendor_id, 'BOOKING_EARNING', b.base_amount - b.discount_amount, p.currency, p.updated_at, p.id, p.booking_id,
       p.booking_reference, 'Earning for booking ' || p.booking_reference, p.updated_at
FROM payments p
JOIN bookings b ON b.id = p.booking_id
WHERE p.payment_method = 'ONLINE' AND p.status = 'SUCCESS' AND b.base_amount - b.discount_amount > 0;

INSERT INTO vendor_wallet_entries (vendor_id, type, amount, currency, available_at, payment_id, booking_id,
                                   booking_reference, description, created_at)
SELECT p.vendor_id, 'PAY_AT_HALL_FEE', -(b.platform_charges + b.tax_amount), p.currency, p.updated_at, p.id,
       p.booking_id, p.booking_reference, 'Platform charges and tax collected at the hall for booking '
       || p.booking_reference, p.updated_at
FROM payments p
JOIN bookings b ON b.id = p.booking_id
WHERE p.payment_method = 'PAY_AT_HALL' AND p.status = 'SUCCESS' AND b.platform_charges + b.tax_amount > 0;
