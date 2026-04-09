-- ============================================================
-- Flyway migration: V3__add_hotel_id_to_restaurant_orders.sql
-- Service  : fb-service (food & beverage)
-- Purpose  : T-FB-01 — IDOR fix: scope restaurant orders to hotel_id
--            so that orders from one hotel are never accessible
--            to users of another hotel (multi-tenant isolation).
--
--            Also fixes the pre-existing CHECK constraint mismatch:
--            the constraint listed 'OPEN','IN_PROGRESS','SERVED','CANCELLED'
--            while the OrderStatus enum uses
--            'PENDING','PREPARED','DELIVERED','BILLED_TO_ROOM'.
-- ============================================================

-- ---------------------------------------------------------------
-- 1. Add hotel_id column (nullable first to avoid NOT NULL violation
--    on existing dev rows, then back-fill, then enforce NOT NULL).
-- ---------------------------------------------------------------
ALTER TABLE restaurant_orders
    ADD COLUMN IF NOT EXISTS hotel_id UUID;

-- Back-fill existing rows with a deterministic placeholder UUID so
-- they satisfy the upcoming NOT NULL constraint in dev/test envs.
UPDATE restaurant_orders
   SET hotel_id = gen_random_uuid()
 WHERE hotel_id IS NULL;

ALTER TABLE restaurant_orders
    ALTER COLUMN hotel_id SET NOT NULL;

COMMENT ON COLUMN restaurant_orders.hotel_id
    IS 'Hotel scope for multi-tenant isolation (T-FB-01). Set server-side from X-Auth-Hotel JWT claim; never accepted from client.';

-- Index for efficient hotel-scoped listing
CREATE INDEX IF NOT EXISTS idx_restaurant_orders_hotel_id
    ON restaurant_orders (hotel_id)
    WHERE active = TRUE;

-- Composite index for the most common hotel + stay query
CREATE INDEX IF NOT EXISTS idx_restaurant_orders_hotel_stay
    ON restaurant_orders (hotel_id, stay_id)
    WHERE active = TRUE;

-- ---------------------------------------------------------------
-- 2. Fix status CHECK constraint (pre-existing mismatch).
--    Old set: OPEN, IN_PROGRESS, SERVED, CANCELLED
--    Correct set (matches OrderStatus enum): PENDING, PREPARED, DELIVERED, BILLED_TO_ROOM
-- ---------------------------------------------------------------
ALTER TABLE restaurant_orders
    DROP CONSTRAINT IF EXISTS chk_restaurant_orders_status;

ALTER TABLE restaurant_orders
    ADD CONSTRAINT chk_restaurant_orders_status
        CHECK (status IN ('PENDING', 'PREPARED', 'DELIVERED', 'BILLED_TO_ROOM'));
