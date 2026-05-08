-- ============================================================
-- Flyway migration: V4__add_version_to_reservations.sql
-- Purpose : Add optimistic-lock version column (T-RES-01)
-- Maps to : @Version Long version in Reservation.java
-- ============================================================

ALTER TABLE reservations
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN reservations.version IS
    'JPA optimistic-lock counter. Incremented on every UPDATE; '
    'a stale-version conflict returns HTTP 409 Conflict.';
