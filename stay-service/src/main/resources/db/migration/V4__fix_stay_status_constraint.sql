-- Migration V4: Fix chk_stays_status constraint to align with StayStatus.java
-- Status allowed in Java: EXPECTED, CHECKED_IN, CHECKED_OUT
-- Status allowed in old SQL: ACTIVE, CHECKED_OUT, CANCELLED

-- 1. Remove the old, restrictive constraint
ALTER TABLE stays DROP CONSTRAINT IF EXISTS chk_stays_status;

-- 2. Migrate existing 'ACTIVE' records to 'CHECKED_IN' (if any)
UPDATE stays SET status = 'CHECKED_IN' WHERE status = 'ACTIVE';

-- 3. Add the new, comprehensive constraint
ALTER TABLE stays ADD CONSTRAINT chk_stays_status
    CHECK (status IN ('EXPECTED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED'));

-- 4. Update index for clarity (re-creating it just in case though the previous one should work)
DROP INDEX IF EXISTS idx_stays_status;
CREATE INDEX idx_stays_status ON stays (status) WHERE active = TRUE;
