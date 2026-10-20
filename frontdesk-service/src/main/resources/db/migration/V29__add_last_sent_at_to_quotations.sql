-- ============================================================
-- Flyway migration: V29__add_last_sent_at_to_quotations.sql
-- Service  : frontdesk-service
-- Purpose  : timestamp (UTC) of the last quotation email send attempt.
--            sendQuotationEmail() refuses another send to the same recipient
--            inside the cooldown window (even via a duplicated quotation), so
--            the recipient cannot be mail-bombed — the prospect address is
--            typed by hand, not verified. Null until the first send attempt.
-- ============================================================

ALTER TABLE quotations ADD COLUMN last_sent_at TIMESTAMP;

COMMENT ON COLUMN quotations.last_sent_at IS 'UTC time of the last quotation email send attempt; null until then. Sends to the same recipient inside the cooldown are refused with HTTP 409.';
