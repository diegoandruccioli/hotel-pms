-- ============================================================
-- Flyway migration: V5__add_owner_role_and_must_change_password.sql
-- Service       : auth-service
-- Changes:
--   1. Extend the role CHECK constraint to include 'OWNER'
--   2. Add must_change_password column (default FALSE)
--      Set TRUE for the seeded ADMIN account so first login forces a
--      password change in pilot deployments.
-- ============================================================

-- 1. Update role constraint to include OWNER
ALTER TABLE user_account
    DROP CONSTRAINT IF EXISTS chk_user_account_role;

ALTER TABLE user_account
    ADD CONSTRAINT chk_user_account_role
        CHECK (role IN ('ADMIN', 'OWNER', 'RECEPTIONIST', 'GUEST'));

COMMENT ON COLUMN user_account.role IS 'Authorization level: ADMIN | OWNER | RECEPTIONIST | GUEST.';

-- 2. Add must_change_password column
ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN user_account.must_change_password
    IS 'When TRUE the user must change their password before using the system. '
       'Set on admin-created accounts and on the default seeded account.';

-- Mark the default seeded ADMIN as requiring a password change
UPDATE user_account
SET    must_change_password = TRUE
WHERE  id = '00000000-0000-0000-0000-000000000000';
