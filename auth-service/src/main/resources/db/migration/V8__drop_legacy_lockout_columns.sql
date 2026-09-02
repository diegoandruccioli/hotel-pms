-- ============================================================
-- Flyway migration: V8__drop_legacy_lockout_columns.sql
-- Service       : auth-service
-- Purpose       : drop the Postgres-based lockout columns added by
--                 V2__add_login_lockout.sql (T-AUTH-02). The lockout
--                 mechanism was moved to Redis (LoginAttemptServiceImpl,
--                 login:fail:<username>:<ip> / login:lock:<username>:<ip>)
--                 before these columns were ever read or written from
--                 application code — dead columns, dead entity fields,
--                 dead repository method removed alongside this migration
--                 (T-AUTH-15 follow-up cleanup)
--                 (feature/secure-coding-hardening)
-- ============================================================

ALTER TABLE user_account
    DROP COLUMN IF EXISTS failed_attempts,
    DROP COLUMN IF EXISTS locked_until;
