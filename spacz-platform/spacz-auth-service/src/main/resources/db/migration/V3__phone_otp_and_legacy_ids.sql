-- Phone-OTP login (docs/MIGRATION_PLAN.md, decision D3) and legacy account import.
ALTER TABLE user_accounts ALTER COLUMN email DROP NOT NULL;
ALTER TABLE user_accounts ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE user_accounts ADD COLUMN phone VARCHAR(20);
ALTER TABLE user_accounts ADD CONSTRAINT uk_user_accounts_phone UNIQUE (phone);
ALTER TABLE user_accounts ADD CONSTRAINT ck_user_accounts_identifier CHECK (email IS NOT NULL OR phone IS NOT NULL);
ALTER TABLE user_accounts ADD COLUMN legacy_login_id BIGINT;
ALTER TABLE user_accounts ADD CONSTRAINT uk_user_accounts_legacy_login UNIQUE (legacy_login_id);

CREATE TABLE otp_challenges (
    id           BIGSERIAL PRIMARY KEY,
    phone        VARCHAR(20) NOT NULL,
    code_hash    VARCHAR(64) NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    attempts     INTEGER     NOT NULL DEFAULT 0,
    consumed_at  TIMESTAMPTZ,
    requested_ip VARCHAR(64),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_otp_challenges_phone_created ON otp_challenges (phone, created_at DESC);
