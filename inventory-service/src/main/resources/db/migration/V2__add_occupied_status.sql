-- ============================================================
-- Flyway migration: V2__add_occupied_status.sql
-- Service       : inventory-service
-- Purpose       : Extend rooms.status CHECK constraint to include
--                 OCCUPIED, set by the stay-service check-in Saga
--                 and cleared to DIRTY at check-out.
-- ============================================================

ALTER TABLE rooms
    DROP CONSTRAINT IF EXISTS chk_rooms_status,
    ADD CONSTRAINT chk_rooms_status
        CHECK (status IN ('CLEAN', 'DIRTY', 'MAINTENANCE', 'OCCUPIED'));

COMMENT ON COLUMN rooms.status IS
    'Operational status: CLEAN = ready; DIRTY = needs cleaning; '
    'MAINTENANCE = not available; OCCUPIED = guest checked in (set by stay-service Saga).';
