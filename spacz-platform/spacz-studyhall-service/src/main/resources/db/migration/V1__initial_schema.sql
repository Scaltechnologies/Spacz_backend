-- spacz_studyhall: vendors, study halls, seating, bookings.
-- vendor_id and user_id hold auth-service account IDs; program_id holds a user-service program ID.
-- None of them have foreign keys: they belong to other services' databases.

CREATE TABLE vendor_profiles (
    id            BIGSERIAL PRIMARY KEY,
    vendor_id     BIGINT        NOT NULL,
    business_name VARCHAR(150)  NOT NULL,
    contact_name  VARCHAR(120)  NOT NULL,
    email         VARCHAR(254)  NOT NULL,
    phone         VARCHAR(20)   NOT NULL,
    address_line  VARCHAR(255),
    city          VARCHAR(80),
    state         VARCHAR(80),
    pincode       VARCHAR(10),
    gst_number    VARCHAR(20),
    description   VARCHAR(2000),
    logo_url      VARCHAR(500),
    status        VARCHAR(20)   NOT NULL,
    status_reason VARCHAR(500),
    approved_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_vendor_profiles_vendor_id UNIQUE (vendor_id),
    CONSTRAINT ck_vendor_profiles_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED', 'ACTIVE', 'INACTIVE'))
);

CREATE TABLE study_halls (
    id                BIGSERIAL PRIMARY KEY,
    vendor_profile_id BIGINT         NOT NULL REFERENCES vendor_profiles (id),
    name              VARCHAR(150)   NOT NULL,
    description       VARCHAR(4000),
    address_line      VARCHAR(255)   NOT NULL,
    city              VARCHAR(80)    NOT NULL,
    state             VARCHAR(80)    NOT NULL,
    pincode           VARCHAR(10),
    latitude          DOUBLE PRECISION,
    longitude         DOUBLE PRECISION,
    contact_phone     VARCHAR(20),
    contact_email     VARCHAR(254),
    price_per_day     NUMERIC(10, 2) NOT NULL,
    rules             VARCHAR(4000),
    status            VARCHAR(20)    NOT NULL,
    status_reason     VARCHAR(500),
    submitted_at      TIMESTAMPTZ,
    approved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_study_halls_price CHECK (price_per_day > 0),
    CONSTRAINT ck_study_halls_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_study_halls_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_study_halls_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'SUSPENDED', 'ACTIVE', 'INACTIVE'))
);

CREATE TABLE study_hall_operating_hours (
    id            BIGSERIAL PRIMARY KEY,
    study_hall_id BIGINT      NOT NULL REFERENCES study_halls (id) ON DELETE CASCADE,
    day_of_week   VARCHAR(10) NOT NULL,
    open_time     TIME,
    close_time    TIME,
    closed        BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_operating_hours_day UNIQUE (study_hall_id, day_of_week),
    CONSTRAINT ck_operating_hours_times CHECK (closed OR (open_time IS NOT NULL AND close_time IS NOT NULL AND close_time > open_time))
);

CREATE TABLE study_hall_images (
    id            BIGSERIAL PRIMARY KEY,
    study_hall_id BIGINT       NOT NULL REFERENCES study_halls (id) ON DELETE CASCADE,
    url           VARCHAR(500) NOT NULL,
    caption       VARCHAR(200),
    display_order INTEGER      NOT NULL DEFAULT 0,
    is_cover      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE amenities (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    icon        VARCHAR(60),
    description VARCHAR(300),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_amenities_code UNIQUE (code),
    CONSTRAINT uk_amenities_name UNIQUE (name)
);

CREATE TABLE study_hall_amenities (
    study_hall_id BIGINT NOT NULL REFERENCES study_halls (id) ON DELETE CASCADE,
    amenity_id    BIGINT NOT NULL REFERENCES amenities (id),
    PRIMARY KEY (study_hall_id, amenity_id)
);

CREATE TABLE study_hall_programs (
    id            BIGSERIAL PRIMARY KEY,
    study_hall_id BIGINT       NOT NULL REFERENCES study_halls (id) ON DELETE CASCADE,
    program_id    BIGINT       NOT NULL,
    program_name  VARCHAR(120) NOT NULL,
    CONSTRAINT uk_study_hall_programs UNIQUE (study_hall_id, program_id)
);

CREATE TABLE seat_layouts (
    id            BIGSERIAL PRIMARY KEY,
    study_hall_id BIGINT      NOT NULL REFERENCES study_halls (id) ON DELETE CASCADE,
    name          VARCHAR(80) NOT NULL,
    total_rows    INTEGER     NOT NULL,
    total_columns INTEGER     NOT NULL,
    display_order INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT ck_seat_layouts_size CHECK (total_rows BETWEEN 1 AND 60 AND total_columns BETWEEN 1 AND 60)
);

CREATE TABLE seats (
    id             BIGSERIAL PRIMARY KEY,
    study_hall_id  BIGINT         NOT NULL REFERENCES study_halls (id) ON DELETE CASCADE,
    seat_layout_id BIGINT         NOT NULL REFERENCES seat_layouts (id) ON DELETE CASCADE,
    seat_number    VARCHAR(20)    NOT NULL,
    row_index      INTEGER        NOT NULL,
    column_index   INTEGER        NOT NULL,
    seat_type      VARCHAR(20)    NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    price_per_day  NUMERIC(10, 2),
    version        BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_seats_position UNIQUE (seat_layout_id, row_index, column_index),
    CONSTRAINT ck_seats_position CHECK (row_index >= 1 AND column_index >= 1),
    CONSTRAINT ck_seats_price CHECK (price_per_day IS NULL OR price_per_day > 0),
    CONSTRAINT ck_seats_status CHECK (status IN ('AVAILABLE', 'MAINTENANCE', 'INACTIVE'))
);
-- Seat numbers are unique per hall, case-insensitively.
CREATE UNIQUE INDEX uk_seats_hall_number ON seats (study_hall_id, lower(seat_number));

CREATE TABLE bookings (
    id                  BIGSERIAL PRIMARY KEY,
    booking_reference   VARCHAR(20)    NOT NULL,
    user_id             BIGINT         NOT NULL,
    study_hall_id       BIGINT         NOT NULL REFERENCES study_halls (id),
    seat_id             BIGINT         NOT NULL REFERENCES seats (id),
    start_date          DATE           NOT NULL,
    end_date            DATE           NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    price_per_day       NUMERIC(10, 2) NOT NULL,
    total_price         NUMERIC(12, 2) NOT NULL,
    hold_expires_at     TIMESTAMPTZ,
    confirmed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_bookings_reference UNIQUE (booking_reference),
    CONSTRAINT ck_bookings_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'EXPIRED'))
);
