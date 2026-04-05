-- ============================================================
-- Flyway migration: V2__add_hotel_id_to_guests.sql
-- Security       : T-GST-01 (IDOR) + T-GST-03 (multi-tenant leak)
-- Change         : add hotel_id to guests for tenant isolation
-- ============================================================

-- Add hotel_id column with a temporary default for existing rows.
-- The default is removed immediately after so new inserts must supply a value.
ALTER TABLE guests
    ADD COLUMN hotel_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';

ALTER TABLE guests
    ALTER COLUMN hotel_id DROP DEFAULT;

COMMENT ON COLUMN guests.hotel_id IS 'Owning hotel UUID. Used to enforce multi-tenant isolation: every query filters by this value. Injected from X-Auth-Hotel gateway header.';

-- Composite index: most queries look up active guests by hotel
CREATE INDEX IF NOT EXISTS idx_guests_hotel_id
    ON guests (hotel_id)
    WHERE active = TRUE;
