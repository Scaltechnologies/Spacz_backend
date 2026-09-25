-- spacz_admin: audit trail of administrative actions.
-- actor_user_id and entity_id reference other services' IDs; no foreign keys by design.

CREATE TABLE audit_logs (
    id             BIGSERIAL PRIMARY KEY,
    actor_user_id  BIGINT        NOT NULL,
    actor_email    VARCHAR(254),
    actor_role     VARCHAR(20)   NOT NULL,
    action         VARCHAR(40)   NOT NULL,
    entity_type    VARCHAR(40)   NOT NULL,
    entity_id      BIGINT,
    description    VARCHAR(1000),
    ip_address     VARCHAR(64),
    user_agent     VARCHAR(300),
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
