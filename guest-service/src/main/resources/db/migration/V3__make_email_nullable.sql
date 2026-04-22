-- ============================================================
-- Flyway migration: V3__make_email_nullable.sql
-- Service       : guest-service
-- Reason        : email is now optional — either email or phone must be
--                 provided, but neither is individually mandatory.
--                 PostgreSQL allows multiple NULLs in a UNIQUE column,
--                 so the uq_guests_email constraint is preserved as-is.
-- ============================================================

ALTER TABLE guests
    ALTER COLUMN email DROP NOT NULL;
