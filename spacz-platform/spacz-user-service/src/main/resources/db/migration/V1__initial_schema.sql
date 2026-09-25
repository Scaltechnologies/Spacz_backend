-- spacz_user: student profiles, exam catalog, preparations, activity.
-- user_id columns hold auth-service account IDs; there is deliberately no FK across services.

CREATE TABLE user_profiles (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT       NOT NULL,
    email                  VARCHAR(254) NOT NULL,
    first_name             VARCHAR(80)  NOT NULL,
    last_name              VARCHAR(80),
    phone                  VARCHAR(20),
    profile_image_url      VARCHAR(500),
    date_of_birth          DATE,
    city                   VARCHAR(80),
    state                  VARCHAR(80),
    bio                    VARCHAR(500),
    education_level        VARCHAR(80),
    preferred_city         VARCHAR(80),
    preferred_study_slot   VARCHAR(20),
    daily_study_hours_goal INTEGER,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_profiles_user_id UNIQUE (user_id),
    CONSTRAINT ck_user_profiles_hours CHECK (daily_study_hours_goal IS NULL OR daily_study_hours_goal BETWEEN 1 AND 16)
);

CREATE TABLE preparation_programs (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(40)   NOT NULL,
    name        VARCHAR(120)  NOT NULL,
    description VARCHAR(1000),
    category    VARCHAR(60)   NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_preparation_programs_code UNIQUE (code),
    CONSTRAINT uk_preparation_programs_name UNIQUE (name)
);

CREATE TABLE user_preparations (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    program_id  BIGINT      NOT NULL REFERENCES preparation_programs (id),
    start_date  DATE,
    target_date DATE,
    status      VARCHAR(20) NOT NULL,
    notes       VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_preparations_user_program UNIQUE (user_id, program_id),
    CONSTRAINT ck_user_preparations_dates CHECK (target_date IS NULL OR start_date IS NULL OR target_date >= start_date)
);

CREATE TABLE user_activities (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    activity_type  VARCHAR(40)  NOT NULL,
    description    VARCHAR(500) NOT NULL,
    reference_type VARCHAR(40),
    reference_id   BIGINT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
