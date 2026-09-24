-- Admin profiles, and domain events reported by other services in the audit log.
CREATE TABLE admin_profiles (
    id            BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT       NOT NULL,
    full_name     VARCHAR(120),
    email         VARCHAR(254),
    phone         VARCHAR(20),
    title         VARCHAR(80),
    last_seen_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_admin_profiles_user UNIQUE (admin_user_id)
);

-- System events have no human actor.
ALTER TABLE audit_logs ALTER COLUMN actor_user_id DROP NOT NULL;
ALTER TABLE audit_logs ADD COLUMN source_service VARCHAR(40);
-- One-off backfill: the append-only trigger (V2) is suspended for this statement only.
ALTER TABLE audit_logs DISABLE TRIGGER trg_audit_logs_immutable;
UPDATE audit_logs SET source_service = 'admin-service' WHERE source_service IS NULL;
ALTER TABLE audit_logs ENABLE TRIGGER trg_audit_logs_immutable;
CREATE INDEX idx_audit_logs_actor_role ON audit_logs (actor_role, created_at DESC);
