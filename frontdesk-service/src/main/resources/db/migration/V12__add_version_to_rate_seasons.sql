-- ============================================================
-- Flyway migration: V12__add_version_to_rate_seasons.sql
-- Service  : frontdesk-service
-- Purpose  : JPA optimistic-lock counter on rate_seasons (Finding #11,
--            security-report.md — MEDIUM). bulkApply's split/trim UPDATE on
--            pre-existing overlapping seasons had no way to detect a lost
--            update between two concurrent admins editing the same season;
--            @Version turns the second concurrent write into a clean 409
--            instead of a silent overwrite, same pattern as
--            reservations.version.
-- ============================================================

ALTER TABLE rate_seasons ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN rate_seasons.version IS 'JPA optimistic-lock counter; stale-version conflict returns HTTP 409.';
