-- Migration V2: Add guest_id and expected_guests, remove guest_name

-- 1. Add new columns allowing null temporarily to accommodate existing rows
ALTER TABLE reservations ADD COLUMN guest_id UUID;
ALTER TABLE reservations ADD COLUMN expected_guests INT;

-- 2. Update existing rows with defaults to satisfy NOT NULL constraint
-- Assign a random UUID for existing guests, and default to 1 expected guest
UPDATE reservations SET guest_id = gen_random_uuid() WHERE guest_id IS NULL;
UPDATE reservations SET expected_guests = 1 WHERE expected_guests IS NULL;

-- 3. Alter columns to NOT NULL
ALTER TABLE reservations ALTER COLUMN guest_id SET NOT NULL;
ALTER TABLE reservations ALTER COLUMN expected_guests SET NOT NULL;

-- 4. Drop the old column
ALTER TABLE reservations DROP COLUMN guest_name;

-- 5. Add comments
COMMENT ON COLUMN reservations.guest_id IS 'UUID reference to the primary guest in guest-service.';
COMMENT ON COLUMN reservations.expected_guests IS 'Number of guests expected for the reservation.';
