-- T-GST-05: GDPR data retention — add consent date to guest profiles.
-- Existing rows are backfilled with created_at (the moment the profile was
-- first registered, which is a legally defensible proxy for the date the
-- guest provided consent at the hotel reception).
ALTER TABLE guests
    ADD COLUMN gdpr_consent_date DATE;

UPDATE guests
SET gdpr_consent_date = created_at::date
WHERE gdpr_consent_date IS NULL;

ALTER TABLE guests
    ALTER COLUMN gdpr_consent_date SET NOT NULL;
