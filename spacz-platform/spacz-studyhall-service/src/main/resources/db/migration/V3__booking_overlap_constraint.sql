-- Database-level guarantee against double booking: no two seat-occupying bookings (PENDING or
-- CONFIRMED) of the same seat may have overlapping inclusive date ranges. This holds even if
-- application-level checks are bypassed or have a bug.
-- btree_gist is a trusted extension (PostgreSQL 13+): the database owner can create it.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings
    ADD CONSTRAINT ex_bookings_seat_no_overlap
    EXCLUDE USING gist (seat_id WITH =, daterange(start_date, end_date, '[]') WITH &&)
    WHERE (status IN ('PENDING', 'CONFIRMED'));
