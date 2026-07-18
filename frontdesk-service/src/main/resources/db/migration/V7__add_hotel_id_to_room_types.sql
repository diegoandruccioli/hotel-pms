-- ============================================================
-- Flyway migration: V7__add_hotel_id_to_room_types.sql
-- Service  : frontdesk-service
-- Purpose  : T-ROOM-02 -- room_types had no hotel_id since V1 baseline
--            (pre-dates the multi-tenant hotel_id convention). Any
--            ADMIN/OWNER of any hotel could read, tamper with (price,
--            name, capacity) or soft-delete another hotel's room-type
--            catalog -- IDOR + cross-tenant write access, not just a
--            missing filter. UNIQUE(name) also blocked two independent
--            hotels from both naming a room type "Deluxe".
--            Same pattern as V4 in fb-service (menu_items).
-- ============================================================

-- ---------------------------------------------------------------
-- 1. hotel_id (nullable first, then back-fill, then NOT NULL)
-- ---------------------------------------------------------------
ALTER TABLE room_types
    ADD COLUMN IF NOT EXISTS hotel_id UUID;

-- Back-fill existing rows with the default hotel UUID used during dev/test
-- (same placeholder used by auth-service V3 and fb-service V4).
UPDATE room_types
   SET hotel_id = '00000000-0000-0000-0000-000000000001'
 WHERE hotel_id IS NULL;

ALTER TABLE room_types
    ALTER COLUMN hotel_id SET NOT NULL;

COMMENT ON COLUMN room_types.hotel_id
    IS 'Hotel scope for multi-tenant isolation (T-ROOM-02). Set server-side; never accepted from client.';

CREATE INDEX IF NOT EXISTS idx_room_types_hotel_id
    ON room_types (hotel_id)
    WHERE active = TRUE;

-- ---------------------------------------------------------------
-- 2. Uniqueness is per-hotel, not global: two independent hotels must
--    both be able to name a room type "Deluxe".
-- ---------------------------------------------------------------
ALTER TABLE room_types
    DROP CONSTRAINT IF EXISTS uq_room_types_name;

ALTER TABLE room_types
    ADD CONSTRAINT uq_room_types_hotel_id_name UNIQUE (hotel_id, name);
