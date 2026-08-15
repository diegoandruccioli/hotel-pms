-- ============================================================
-- Flyway migration: V13__add_version_to_quotations.sql
-- Service  : frontdesk-service
-- Purpose  : JPA optimistic-lock counter on quotations (Finding #6,
--            security-report.md — HIGH). convertToReservation() reads the
--            quotation unlocked, checks status, then writes ACCEPTED at the
--            end — two concurrent convert calls both pass the same guard and
--            both create a reservation. @Version turns the second concurrent
--            save() into a clean 409 instead of a silent double-conversion,
--            same pattern as reservations.version.
-- ============================================================

ALTER TABLE quotations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN quotations.version IS 'JPA optimistic-lock counter; stale-version conflict returns HTTP 409.';
