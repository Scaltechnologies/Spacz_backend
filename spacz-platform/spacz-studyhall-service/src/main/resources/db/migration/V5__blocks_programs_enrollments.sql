-- Consolidation of spacz-partner-service into studyhall-service (docs/MIGRATION_PLAN.md).
--  * seat_layouts become blocks (legacy "block"): own daily/monthly prices and amenities
--  * the program catalog moves here from user-service
--  * monthly bookings, RESERVED seats, DRAFT vendors, enrollments (hall memberships)
--  * legacy_* columns keep the MySQL IDs for the one-time migration (idempotency, traceability)

-- 1. Program catalog. IDs 1..18 match user-service's former catalog so existing references stay valid.
CREATE TABLE programs (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(40)   NOT NULL,
    name        VARCHAR(120)  NOT NULL,
    description VARCHAR(1000),
    category    VARCHAR(60)   NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_programs_code UNIQUE (code),
    CONSTRAINT uk_programs_name UNIQUE (name)
);
INSERT INTO programs (id, code, name, description, category) VALUES
    (1,  'UPSC_CSE',   'UPSC Civil Services',   'IAS / IPS / IFS preliminary and main examinations',          'Civil Services'),
    (2,  'STATE_PSC',  'State PSC',             'State public service commission examinations',               'Civil Services'),
    (3,  'SSC_CGL',    'SSC CGL',               'Staff Selection Commission combined graduate level',         'Government Jobs'),
    (4,  'SSC_CHSL',   'SSC CHSL',              'Staff Selection Commission higher secondary level',          'Government Jobs'),
    (5,  'RRB_NTPC',   'RRB NTPC',              'Railway recruitment non-technical popular categories',       'Government Jobs'),
    (6,  'IBPS_PO',    'IBPS PO',               'Probationary officer examination for public sector banks',   'Banking'),
    (7,  'SBI_PO',     'SBI PO',                'State Bank of India probationary officer examination',       'Banking'),
    (8,  'IBPS_CLERK', 'IBPS Clerk',            'Clerical cadre examination for public sector banks',         'Banking'),
    (9,  'GATE',       'GATE',                  'Graduate Aptitude Test in Engineering',                      'Engineering'),
    (10, 'JEE_MAIN',   'JEE Main',              'Joint Entrance Examination (Main)',                          'Engineering'),
    (11, 'JEE_ADV',    'JEE Advanced',          'Joint Entrance Examination (Advanced)',                      'Engineering'),
    (12, 'NEET_UG',    'NEET UG',               'National Eligibility cum Entrance Test (UG)',                'Medical'),
    (13, 'NEET_PG',    'NEET PG',               'National Eligibility cum Entrance Test (PG)',                'Medical'),
    (14, 'CAT',        'CAT',                   'Common Admission Test for management programmes',            'Management'),
    (15, 'CA',         'Chartered Accountancy', 'ICAI foundation, intermediate and final',                    'Professional'),
    (16, 'CLAT',       'CLAT',                  'Common Law Admission Test',                                  'Law'),
    (17, 'GRE_GMAT',   'GRE / GMAT',            'Graduate admission tests for study abroad',                  'Study Abroad'),
    (18, 'OTHER',      'Other / Self Study',    'Any other competitive or professional exam',                 'Other');
-- Programs that halls referenced but that are not in the seed (created later by admins in user-service).
INSERT INTO programs (id, code, name, category)
SELECT DISTINCT ON (shp.program_id) shp.program_id, 'PROGRAM_' || shp.program_id,
       shp.program_name || ' (' || shp.program_id || ')', 'Other'
FROM study_hall_programs shp
WHERE NOT EXISTS (SELECT 1 FROM programs p WHERE p.id = shp.program_id)
ON CONFLICT DO NOTHING;
SELECT setval(pg_get_serial_sequence('programs', 'id'), (SELECT max(id) FROM programs));
INSERT INTO programs (code, name, description, category) VALUES
    ('EAMCET',   'TS / AP EAMCET',  'Engineering, Agriculture & Medical Common Entrance Test',  'Engineering'),
    ('ICET',     'TS / AP ICET',    'Integrated Common Entrance Test (MBA / MCA)',             'Management'),
    ('GROUP_1',  'APPSC / TSPSC Group 1', 'State Group 1 services examination',                'Civil Services'),
    ('UGC_NET',  'UGC NET',         'National Eligibility Test for lecturership / JRF',        'Teaching'),
    ('CTET_TET', 'CTET / TET',      'Teacher eligibility tests',                               'Teaching')
ON CONFLICT DO NOTHING;

-- study_hall_programs becomes a plain join table with a real FK (same database now).
ALTER TABLE study_hall_programs DROP CONSTRAINT uk_study_hall_programs;
ALTER TABLE study_hall_programs DROP COLUMN id;
ALTER TABLE study_hall_programs DROP COLUMN program_name;
ALTER TABLE study_hall_programs ADD PRIMARY KEY (study_hall_id, program_id);
ALTER TABLE study_hall_programs ADD CONSTRAINT fk_study_hall_programs_program FOREIGN KEY (program_id) REFERENCES programs (id);

-- 2. Vendors: DRAFT status, optional fields until submission, legacy owner id.
ALTER TABLE vendor_profiles ALTER COLUMN business_name DROP NOT NULL;
ALTER TABLE vendor_profiles ALTER COLUMN contact_name DROP NOT NULL;
ALTER TABLE vendor_profiles ALTER COLUMN email DROP NOT NULL;
ALTER TABLE vendor_profiles ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE vendor_profiles ADD COLUMN submitted_at TIMESTAMPTZ;
ALTER TABLE vendor_profiles ADD COLUMN legacy_owner_id BIGINT;
ALTER TABLE vendor_profiles ADD CONSTRAINT uk_vendor_profiles_legacy_owner UNIQUE (legacy_owner_id);
ALTER TABLE vendor_profiles DROP CONSTRAINT ck_vendor_profiles_status;
ALTER TABLE vendor_profiles ADD CONSTRAINT ck_vendor_profiles_status
    CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED', 'ACTIVE', 'INACTIVE'));
UPDATE vendor_profiles SET submitted_at = created_at WHERE status <> 'DRAFT';

-- 3. Study halls: optional default prices (blocks/seats may carry them), monthly price, legacy id.
ALTER TABLE study_halls ALTER COLUMN price_per_day DROP NOT NULL;
ALTER TABLE study_halls ALTER COLUMN address_line DROP NOT NULL;
ALTER TABLE study_halls ALTER COLUMN city DROP NOT NULL;
ALTER TABLE study_halls ALTER COLUMN state DROP NOT NULL;
ALTER TABLE study_halls DROP CONSTRAINT ck_study_halls_price;
ALTER TABLE study_halls ADD COLUMN price_per_month NUMERIC(10, 2);
ALTER TABLE study_halls ADD CONSTRAINT ck_study_halls_prices
    CHECK ((price_per_day IS NULL OR price_per_day > 0) AND (price_per_month IS NULL OR price_per_month > 0));
ALTER TABLE study_halls ADD COLUMN legacy_property_id BIGINT;
ALTER TABLE study_halls ADD CONSTRAINT uk_study_halls_legacy_property UNIQUE (legacy_property_id);
ALTER TABLE study_hall_images ADD COLUMN legacy_image_id BIGINT;
ALTER TABLE study_hall_images ADD CONSTRAINT uk_study_hall_images_legacy UNIQUE (legacy_image_id);

-- 4. Seat layouts become blocks.
ALTER TABLE seat_layouts RENAME TO blocks;
ALTER TABLE blocks RENAME CONSTRAINT ck_seat_layouts_size TO ck_blocks_size;
ALTER TABLE blocks ADD COLUMN daily_price NUMERIC(10, 2);
ALTER TABLE blocks ADD COLUMN monthly_price NUMERIC(10, 2);
ALTER TABLE blocks ADD CONSTRAINT ck_blocks_prices
    CHECK ((daily_price IS NULL OR daily_price > 0) AND (monthly_price IS NULL OR monthly_price > 0));
ALTER TABLE blocks ADD COLUMN legacy_block_id BIGINT;
ALTER TABLE blocks ADD CONSTRAINT uk_blocks_legacy UNIQUE (legacy_block_id);
CREATE TABLE block_amenities (
    block_id   BIGINT NOT NULL REFERENCES blocks (id) ON DELETE CASCADE,
    amenity_id BIGINT NOT NULL REFERENCES amenities (id),
    PRIMARY KEY (block_id, amenity_id)
);

ALTER TABLE seats RENAME COLUMN seat_layout_id TO block_id;
ALTER TABLE seats RENAME CONSTRAINT uk_seats_position TO uk_seats_block_position;
ALTER TABLE seats ADD COLUMN price_per_month NUMERIC(10, 2);
ALTER TABLE seats ADD CONSTRAINT ck_seats_monthly_price CHECK (price_per_month IS NULL OR price_per_month > 0);
ALTER TABLE seats ADD COLUMN legacy_seat_id BIGINT;
ALTER TABLE seats ADD CONSTRAINT uk_seats_legacy UNIQUE (legacy_seat_id);
ALTER TABLE seats DROP CONSTRAINT ck_seats_status;
ALTER TABLE seats ADD CONSTRAINT ck_seats_status CHECK (status IN ('AVAILABLE', 'RESERVED', 'MAINTENANCE', 'INACTIVE'));

-- 5. Bookings: DAILY / MONTHLY plans, optional exam, legacy id.
ALTER TABLE bookings RENAME COLUMN price_per_day TO unit_price;
ALTER TABLE bookings ADD COLUMN plan VARCHAR(10) NOT NULL DEFAULT 'DAILY';
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_plan CHECK (plan IN ('DAILY', 'MONTHLY'));
ALTER TABLE bookings ADD COLUMN program_id BIGINT REFERENCES programs (id);
ALTER TABLE bookings ADD COLUMN legacy_booking_id BIGINT;
ALTER TABLE bookings ADD CONSTRAINT uk_bookings_legacy UNIQUE (legacy_booking_id);

-- 6. Enrollments: a student's membership of a hall (the vendor's student list).
CREATE TABLE enrollments (
    id            BIGSERIAL PRIMARY KEY,
    study_hall_id BIGINT       NOT NULL REFERENCES study_halls (id) ON DELETE CASCADE,
    user_id       BIGINT,
    guest_name    VARCHAR(120),
    guest_phone   VARCHAR(20),
    guest_email   VARCHAR(254),
    program_id    BIGINT REFERENCES programs (id),
    seat_id       BIGINT REFERENCES seats (id),
    plan          VARCHAR(10)  NOT NULL,
    start_date    DATE         NOT NULL,
    end_date      DATE,
    status        VARCHAR(20)  NOT NULL,
    source        VARCHAR(20)  NOT NULL,
    notes         VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_enrollments_student CHECK (user_id IS NOT NULL OR guest_name IS NOT NULL),
    CONSTRAINT ck_enrollments_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_enrollments_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_enrollments_source CHECK (source IN ('BOOKING', 'WALK_IN')),
    CONSTRAINT ck_enrollments_plan CHECK (plan IN ('DAILY', 'MONTHLY'))
);
-- One active membership per student per hall.
CREATE UNIQUE INDEX uk_enrollments_active_member ON enrollments (study_hall_id, user_id)
    WHERE status = 'ACTIVE' AND user_id IS NOT NULL;
CREATE INDEX idx_enrollments_hall_status ON enrollments (study_hall_id, status);
CREATE INDEX idx_enrollments_user ON enrollments (user_id);
CREATE INDEX idx_enrollments_active_end ON enrollments (end_date) WHERE status = 'ACTIVE';
CREATE INDEX idx_programs_category ON programs (category) WHERE active;
