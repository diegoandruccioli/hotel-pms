-- Add optimistic-locking version column to prevent concurrent update races
-- (checkout + F&B order arriving at the same time on the same invoice).
-- DEFAULT 0 ensures all existing rows are valid immediately; NOT NULL enforced.
ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
