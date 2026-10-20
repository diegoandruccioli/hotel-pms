-- ============================================================
-- Flyway migration: V28__add_reservation_id_to_quotations.sql
-- Service  : frontdesk-service
-- Purpose  : remember which reservation an accepted quotation was converted
--            into, so the UI can link the quotation to it. Nullable: quotations
--            accepted before this migration have no recorded reservation, and
--            the id is a plain reference, not a FK (reservations are soft-deleted).
-- ============================================================

ALTER TABLE quotations ADD COLUMN reservation_id UUID;

COMMENT ON COLUMN quotations.reservation_id IS 'Reservation created when the quotation was converted; null until then and for conversions made before V28.';
