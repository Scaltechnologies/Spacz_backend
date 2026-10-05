-- Admin-managed content shown in the student app: offers (also applied to booking prices by
-- studyhall-service) and platform updates (the "Updates" notifications tab).

CREATE TABLE offers (
    id                 BIGSERIAL PRIMARY KEY,
    title              VARCHAR(120)  NOT NULL,
    description        VARCHAR(1000),
    image_url          VARCHAR(500),
    discount_type      VARCHAR(20)   NOT NULL,
    discount_value     NUMERIC(10,2) NOT NULL,
    max_discount       NUMERIC(10,2),
    min_booking_amount NUMERIC(12,2),
    applicable_plan    VARCHAR(10)   NOT NULL DEFAULT 'ANY',
    first_booking_only BOOLEAN       NOT NULL DEFAULT FALSE,
    terms              VARCHAR(1000),
    start_at           TIMESTAMPTZ   NOT NULL,
    end_at             TIMESTAMPTZ   NOT NULL,
    active             BOOLEAN       NOT NULL DEFAULT TRUE,
    archived           BOOLEAN       NOT NULL DEFAULT FALSE,
    priority           INTEGER       NOT NULL DEFAULT 0,
    created_by         BIGINT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_offers_discount_type CHECK (discount_type IN ('PERCENTAGE', 'FLAT')),
    CONSTRAINT ck_offers_discount_value CHECK (discount_value > 0
        AND (discount_type <> 'PERCENTAGE' OR discount_value <= 100)),
    CONSTRAINT ck_offers_plan CHECK (applicable_plan IN ('ANY', 'DAILY', 'MONTHLY')),
    CONSTRAINT ck_offers_window CHECK (end_at > start_at)
);
CREATE INDEX idx_offers_live ON offers (active, archived, start_at, end_at);

CREATE TABLE platform_updates (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(120)  NOT NULL,
    description  VARCHAR(2000) NOT NULL,
    published    BOOLEAN       NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    archived     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by   BIGINT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_platform_updates_live ON platform_updates (published, archived, published_at DESC);
