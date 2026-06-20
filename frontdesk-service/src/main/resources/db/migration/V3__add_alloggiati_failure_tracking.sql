-- Today a failed Alloggiati Web submission is only logged (ERROR level) and
-- never surfaced to staff — a hotel can be non-compliant with the TULPS
-- reporting obligation for days without anyone noticing. These columns let
-- the per-stay badge distinguish FAILED from NOT_ATTEMPTED, and let the
-- Dashboard surface a visible alert for any unresolved failure.
ALTER TABLE stays
    ADD COLUMN alloggiati_send_failed BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN alloggiati_failure_reason VARCHAR(500);
