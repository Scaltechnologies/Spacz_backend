-- The exam/course catalog moved to studyhall-service (docs/MIGRATION_PLAN.md). A student's choices
-- keep the program ID plus a code/name snapshot; IDs are unchanged because studyhall seeded the same IDs.

CREATE TABLE user_programs (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    program_id   BIGINT       NOT NULL,
    program_code VARCHAR(40),
    program_name VARCHAR(120) NOT NULL,
    start_date   DATE,
    target_date  DATE,
    status       VARCHAR(20)  NOT NULL,
    notes        VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_programs_user_program UNIQUE (user_id, program_id),
    CONSTRAINT ck_user_programs_dates CHECK (target_date IS NULL OR start_date IS NULL OR target_date >= start_date)
);

INSERT INTO user_programs (id, user_id, program_id, program_code, program_name, start_date, target_date, status, notes,
                           created_at, updated_at)
SELECT up.id, up.user_id, up.program_id, pp.code, pp.name, up.start_date, up.target_date, up.status, up.notes,
       up.created_at, up.updated_at
FROM user_preparations up
JOIN preparation_programs pp ON pp.id = up.program_id;
SELECT setval(pg_get_serial_sequence('user_programs', 'id'), COALESCE((SELECT max(id) FROM user_programs), 0) + 1, false);

DROP TABLE user_preparations;
DROP TABLE preparation_programs;

CREATE INDEX idx_user_programs_user ON user_programs (user_id);

UPDATE user_activities SET activity_type = replace(activity_type, 'PREPARATION_', 'PROGRAM_')
WHERE activity_type LIKE 'PREPARATION_%';
UPDATE user_activities SET reference_type = 'USER_PROGRAM' WHERE reference_type = 'PREPARATION';

-- Phone-OTP students may have no email.
ALTER TABLE user_profiles ALTER COLUMN email DROP NOT NULL;
