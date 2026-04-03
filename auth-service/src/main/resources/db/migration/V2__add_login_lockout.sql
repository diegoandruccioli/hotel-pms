-- ============================================================
-- Flyway migration: V2__add_login_lockout.sql
-- Service       : auth-service
-- Purpose       : brute-force lockout columns — T-AUTH-02
--                 (feature/secure-coding-hardening)
-- ============================================================

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS failed_attempts INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until   TIMESTAMPTZ;

COMMENT ON COLUMN user_account.failed_attempts IS 'Consecutive failed login attempts since the last successful login. Reset to 0 on success.';
COMMENT ON COLUMN user_account.locked_until    IS 'UTC instant until which the account is locked. NULL = not locked.';
