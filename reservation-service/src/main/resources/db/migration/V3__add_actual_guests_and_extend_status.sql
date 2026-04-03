-- Migration V3: Add actual_guests and extend allowed reservation statuses

-- 1. Add actual_guests with default 0
ALTER TABLE reservations
    ADD COLUMN IF NOT EXISTS actual_guests INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN reservations.actual_guests IS 'Number of guests actually checked in (derived from stays).';

-- 2. Update status CHECK constraint to include PARTIALLY_CHECKED_IN
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_reservations_status'
    ) THEN
        ALTER TABLE reservations DROP CONSTRAINT chk_reservations_status;
    END IF;
END $$;

ALTER TABLE reservations
    ADD CONSTRAINT chk_reservations_status CHECK (status IN (
        'PENDING',
        'CONFIRMED',
        'PARTIALLY_CHECKED_IN',
        'CHECKED_IN',
        'CHECKED_OUT',
        'CANCELLED',
        'NO_SHOW'
    ));

