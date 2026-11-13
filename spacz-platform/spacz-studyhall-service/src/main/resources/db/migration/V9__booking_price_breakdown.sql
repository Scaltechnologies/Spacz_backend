-- Server-side price breakdown per booking: total_price = base_amount - discount_amount
-- + platform_charges + tax_amount. offer_id refers to admin-service offers (no cross-service FK).
ALTER TABLE bookings ADD COLUMN base_amount      NUMERIC(12,2);
ALTER TABLE bookings ADD COLUMN discount_amount  NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN platform_charges NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN tax_amount       NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN offer_id         BIGINT;
ALTER TABLE bookings ADD COLUMN offer_title      VARCHAR(120);

-- Existing bookings had no discounts, fees or taxes.
UPDATE bookings SET base_amount = total_price WHERE base_amount IS NULL;
ALTER TABLE bookings ALTER COLUMN base_amount SET NOT NULL;

ALTER TABLE bookings ADD CONSTRAINT ck_bookings_price_breakdown
    CHECK (total_price = base_amount - discount_amount + platform_charges + tax_amount);
