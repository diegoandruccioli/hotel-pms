-- ============================================================
-- Flyway migration: V3__rooms_hotel_id_not_null.sql
-- Service  : inventory-service
-- Purpose  : Enforce multi-tenant isolation by making hotel_id
--            NOT NULL on rooms, as required by CLAUDE.md.
-- Notes    : Back-fills any legacy NULL rows using gen_random_uuid()
--            so existing data is not blocked by the constraint.
-- ============================================================

-- Back-fill any rows that might have hotel_id = NULL (dev/test data)
UPDATE rooms SET hotel_id = gen_random_uuid() WHERE hotel_id IS NULL;

-- Apply NOT NULL constraint
ALTER TABLE rooms
    ALTER COLUMN hotel_id SET NOT NULL;

-- Index on hotel_id for efficient per-hotel queries
CREATE INDEX IF NOT EXISTS idx_rooms_hotel_id
    ON rooms (hotel_id)
    WHERE active = TRUE;

COMMENT ON COLUMN rooms.hotel_id IS
    'Hotel identifier for multi-tenant isolation. NOT NULL — every room belongs to exactly one hotel.';
