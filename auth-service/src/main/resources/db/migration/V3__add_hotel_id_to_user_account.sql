-- ============================================================
-- Flyway migration: V3__add_hotel_id_to_user_account.sql
-- Service       : auth-service
-- Purpose       : Multi-tenant isolation — T-IDOR-GW-01
--                 Adds hotel_id to user_account so the JWT can
--                 carry a hotelId claim and the API Gateway can
--                 inject X-Auth-Hotel on every downstream request.
--                 (feature/secure-coding-hardening)
-- ============================================================

-- Step 1: add column nullable first to avoid locking issues on existing rows
ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS hotel_id UUID;

-- Step 2: backfill existing rows with the default admin hotel UUID
UPDATE user_account
   SET hotel_id = '00000000-0000-0000-0000-000000000001'
 WHERE hotel_id IS NULL;

-- Step 3: enforce NOT NULL now that every row has a value
ALTER TABLE user_account
    ALTER COLUMN hotel_id SET NOT NULL;

COMMENT ON COLUMN user_account.hotel_id IS 'Tenant identifier. Propagated to JWT claim hotelId and forwarded by the gateway as X-Auth-Hotel for downstream multi-tenant isolation.';

-- Index for tenant-scoped administrative queries
CREATE INDEX IF NOT EXISTS idx_user_account_hotel_id
    ON user_account (hotel_id)
    WHERE active = TRUE;
