-- Admin-entered payment gateway credentials. Secrets are AES-256-GCM ciphertext (see
-- SecretEncryptionService), never plaintext. key_id is public (embedded in checkout) and stored as-is.
CREATE TABLE payment_gateway_configs (
    id                        BIGSERIAL    PRIMARY KEY,
    provider                  VARCHAR(30)  NOT NULL,
    mode                      VARCHAR(10)  NOT NULL DEFAULT 'TEST',
    key_id                    VARCHAR(100) NOT NULL,
    key_secret_encrypted      TEXT         NOT NULL,
    webhook_secret_encrypted  TEXT,
    is_active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_by                BIGINT,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_pgc_provider UNIQUE (provider),
    CONSTRAINT ck_pgc_mode CHECK (mode IN ('TEST', 'LIVE'))
);

-- Generic platform settings (key/value), seeded with PAYMENT_MODE. Values are validated in code,
-- deliberately not by a CHECK constraint: enum-mirroring CHECKs drift out of sync with the code.
CREATE TABLE platform_settings (
    id            BIGSERIAL    PRIMARY KEY,
    setting_key   VARCHAR(100) NOT NULL,
    setting_value VARCHAR(500) NOT NULL,
    description   VARCHAR(500),
    updated_by    BIGINT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_platform_settings_key UNIQUE (setting_key)
);

INSERT INTO platform_settings (setting_key, setting_value, description)
VALUES ('PAYMENT_MODE', 'BOTH', 'Which payment method(s) checkout accepts: ONLINE_ONLY, PAY_AT_HALL_ONLY, or BOTH');
