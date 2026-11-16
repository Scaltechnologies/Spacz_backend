-- Notifications for every SPACZ app (student app, Partner/vendor app, admin panel).
--
-- A notification is either personal (recipient_user_id set) or a broadcast to every account of
-- an audience (recipient_user_id NULL; audience USER, VENDOR, ADMIN or ALL). Personal read state
-- lives on the row; broadcast read/dismiss state lives in notification_receipts, one row per
-- account that read or dismissed it.

CREATE TABLE notifications (
    id                BIGSERIAL PRIMARY KEY,
    recipient_user_id BIGINT,
    audience          VARCHAR(10)   NOT NULL,
    category          VARCHAR(20)   NOT NULL,
    type              VARCHAR(60)   NOT NULL,
    title             VARCHAR(160)  NOT NULL,
    body              VARCHAR(1000) NOT NULL,
    image_url         VARCHAR(500),
    entity_type       VARCHAR(40),
    entity_id         BIGINT,
    data_json         TEXT,
    high_priority     BOOLEAN       NOT NULL DEFAULT FALSE,
    read_at           TIMESTAMPTZ,
    source_service    VARCHAR(40)   NOT NULL,
    created_by        BIGINT,
    dedup_key         VARCHAR(120),
    push_requested    BOOLEAN       NOT NULL DEFAULT FALSE,
    push_status       VARCHAR(20)   NOT NULL,
    push_success      INT           NOT NULL DEFAULT 0,
    push_failure      INT           NOT NULL DEFAULT 0,
    pushed_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_notifications_dedup UNIQUE (dedup_key),
    CONSTRAINT ck_notifications_audience CHECK (audience IN ('USER', 'VENDOR', 'ADMIN', 'ALL')),
    CONSTRAINT ck_notifications_personal_read CHECK (recipient_user_id IS NOT NULL OR read_at IS NULL)
);
-- Personal inbox, newest first.
CREATE INDEX idx_notifications_recipient ON notifications (recipient_user_id, created_at DESC)
    WHERE recipient_user_id IS NOT NULL;
-- Broadcasts by audience, newest first.
CREATE INDEX idx_notifications_broadcast ON notifications (audience, created_at DESC)
    WHERE recipient_user_id IS NULL;
CREATE INDEX idx_notifications_created ON notifications (created_at);
-- Admin "sent" history.
CREATE INDEX idx_notifications_created_by ON notifications (created_by, created_at DESC)
    WHERE created_by IS NOT NULL;

CREATE TABLE notification_receipts (
    id              BIGSERIAL PRIMARY KEY,
    notification_id BIGINT      NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    user_id         BIGINT      NOT NULL,
    read_at         TIMESTAMPTZ NOT NULL,
    dismissed_at    TIMESTAMPTZ,
    CONSTRAINT uk_notification_receipts UNIQUE (notification_id, user_id)
);
CREATE INDEX idx_notification_receipts_user ON notification_receipts (user_id);

-- FCM registration tokens. A token belongs to one account at a time: when another account logs in
-- on the same device, the token moves to that account.
CREATE TABLE device_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    role         VARCHAR(10)  NOT NULL,
    app          VARCHAR(20)  NOT NULL,
    platform     VARCHAR(10)  NOT NULL,
    token        VARCHAR(512) NOT NULL,
    app_version  VARCHAR(40),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_device_tokens_token UNIQUE (token),
    CONSTRAINT ck_device_tokens_role CHECK (role IN ('USER', 'VENDOR', 'ADMIN'))
);
CREATE INDEX idx_device_tokens_user ON device_tokens (user_id);
CREATE INDEX idx_device_tokens_role ON device_tokens (role, id);

-- Push opt-outs per category. No row = push enabled (in-app notifications are always kept).
CREATE TABLE notification_preferences (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    category     VARCHAR(20) NOT NULL,
    push_enabled BOOLEAN     NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_notification_preferences UNIQUE (user_id, category)
);
