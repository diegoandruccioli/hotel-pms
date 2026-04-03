-- Migration V3: Add traveller_type and travel_purpose to stay_guests

ALTER TABLE stay_guests
    ADD COLUMN IF NOT EXISTS traveller_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS travel_purpose VARCHAR(100);

COMMENT ON COLUMN stay_guests.traveller_type IS 'Traveller classification (OSPITE_SINGOLO, CAPOFAMIGLIA, CAPOGRUPPO).';
COMMENT ON COLUMN stay_guests.travel_purpose IS 'Free-text or coded purpose of travel.';

