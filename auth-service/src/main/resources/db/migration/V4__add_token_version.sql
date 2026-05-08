-- ============================================================
-- Flyway migration: V4__add_token_version.sql
-- Service       : auth-service
-- Purpose       : Session invalidation on password change (T-AUTH-04 residuo)
--                 token_version is embedded as claim "tv" in every JWT.
--                 On password change the version is incremented; the new
--                 value is cached in Redis under "user:tv:<username>".
--                 AuthServiceImpl.refresh() rejects tokens whose "tv" claim
--                 diverges from the Redis-cached value, revoking all sessions
--                 that pre-date the password change.
--                 (feature/secure-coding-hardening)
-- ============================================================

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS token_version INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN user_account.token_version IS
    'Monotonically incrementing counter embedded in JWT "tv" claim. '
    'Incrementing it (on password change) invalidates all previously issued '
    'tokens for this user without requiring a per-JTI blacklist sweep.';
