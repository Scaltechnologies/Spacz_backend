-- Sample legacy data with the edge cases the migration must handle.
INSERT INTO user_login (login_id, is_owner_registered, otp, phone_number) VALUES
  (1, b'1', '32109', '9876543210'),   -- owner Ravi
  (2, b'0', '00009', '9000000000'),   -- aspirant Asha (matched by phone)
  (3, b'0', '11113', '9111111111');   -- login without any profile → skipped
INSERT INTO owner (owner_id, address, owner_email, owner_name, owner_phone_number, login_id) VALUES
  (10, 'Ameerpet, Hyderabad', 'ravi@example.com', 'Ravi Kumar', '9876543210', 1),
  (11, 'Kukatpally', NULL, 'Sita Devi', '9222222222', NULL),   -- no login row: phone from owner
  (12, NULL, NULL, 'Ghost Owner', NULL, NULL);                -- nothing to log in with → skipped
INSERT INTO property (property_id, address, google_coordinates, property_name, owner_id) VALUES
  (100, 'Plot 4, Ameerpet', '17.4375,78.4483', 'Green Valley Study Center', 10),
  (101, 'SR Nagar', 'not-a-coordinate', 'Green Valley Annex', 10),
  (102, 'KPHB', '17.4948, 78.3996', 'Sita Library', 11);
INSERT INTO image (image_id, image_url, property_id) VALUES
  (1000, 'https://cdn.example.com/100/front.jpg', 100),
  (1001, 'https://cdn.example.com/100/hall.jpg', 100),
  (1002, NULL, 101);                                           -- empty URL → skipped with warning
INSERT INTO block (block_id, block_daily_price, block_monthly_price, block_name, property_id) VALUES
  (200, 150, 2500, 'AC Hall', 100),
  (201, 100, 1800, 'Silent Room', 100),
  (202, 0, 0, 'Unpriced', 101),                                -- no price → warning
  (203, 120, 2000, 'Main', 102);
INSERT INTO amenity (amenity_id, ac, lockers, newspapers, water, wifi, block_id) VALUES
  (300, b'1', b'0', b'1', b'1', b'1', 200),
  (301, b'0', b'1', b'0', b'1', b'0', 201);
INSERT INTO seat (seat_id, is_reserved, seat_number, seat_price, block_id) VALUES
  (400, b'0', 'A1', 0, 200), (401, b'1', 'A2', 0, 200), (402, b'0', 'A3', 200, 200),
  (403, b'0', 'A1', 0, 201),                                   -- duplicate number in the same property → renamed
  (404, b'0', NULL, 0, 201),                                   -- no number → S404
  (405, b'0', 'B1', 0, 202),
  (406, b'0', '1', 0, 203), (407, b'0', '2', 0, 203);
INSERT INTO aspirant_user (aspirant_user_id, aadhar_number, current_address, email, name, permanent_address, phone_number) VALUES
  (500, '1234-5678-9012', 'Hostel 3', 'asha@example.com', 'Asha Rao', 'Vizag', '9000000000'),
  (501, NULL, NULL, NULL, 'Kiran', NULL, '9333333333');
INSERT INTO booking (booking_id, end_date, start_date, aspirant_user_id, seat_id) VALUES
  (600, '2030-01-31', '2030-01-01', 500, 400),
  (601, '2030-02-28', '2030-02-01', 501, 406),
  (602, NULL, NULL, 501, 407);                                 -- incomplete → skipped
