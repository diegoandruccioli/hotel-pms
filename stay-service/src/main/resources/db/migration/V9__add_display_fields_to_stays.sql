-- ============================================================
-- Flyway migration: V9__add_display_fields_to_stays.sql
-- Service  : stay-service
-- Purpose  : Denormalize guest name and room number into stays at check-in
--            so the stays list can show human-readable info without
--            extra Feign calls at query time.
--            Existing stays will have NULL values; the UI falls back
--            to truncated IDs for legacy rows.
-- ============================================================

ALTER TABLE stays
    ADD COLUMN IF NOT EXISTS guest_display_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS room_number        VARCHAR(50);

COMMENT ON COLUMN stays.guest_display_name
    IS 'Denormalized "Cognome Nome" of the primary guest, captured at check-in.';
COMMENT ON COLUMN stays.room_number
    IS 'Denormalized room number captured at check-in from inventory-service.';
