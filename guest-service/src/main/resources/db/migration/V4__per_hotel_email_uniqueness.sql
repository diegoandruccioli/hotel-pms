-- ============================================================
-- Flyway migration: V4__per_hotel_email_uniqueness.sql
-- Service       : guest-service
-- Change        : Replace global UNIQUE(email) with a per-hotel
--                 composite uniqueness constraint.
--                 The same guest email can exist across different hotels;
--                 it must be unique only within a single hotel.
-- Note          : A partial index (WHERE email IS NOT NULL) is used so
--                 that multiple guests with no email can coexist within
--                 the same hotel without violating the constraint.
-- ============================================================

-- Drop the global unique constraint created in V1
ALTER TABLE guests
    DROP CONSTRAINT IF EXISTS uq_guests_email;

-- Drop the plain email index (replaced below)
DROP INDEX IF EXISTS idx_guests_email;

-- New: composite partial unique index — (email, hotel_id) unique per hotel,
-- NULLs excluded so multiple null-email guests per hotel are allowed.
CREATE UNIQUE INDEX IF NOT EXISTS uq_guests_email_per_hotel
    ON guests (email, hotel_id)
    WHERE email IS NOT NULL;

COMMENT ON INDEX uq_guests_email_per_hotel
    IS 'Enforces that the same e-mail address may not be used twice within a single hotel, while allowing the same address across different hotels and allowing NULL e-mail for multiple guests.';
