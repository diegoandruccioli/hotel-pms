-- Bug fix: removing a guest added mid-stay (V20's guest lifecycle) left the tourist-tax
-- charge posted for them (rectifyForGuestAdded) untouched — the invoice kept billing for
-- a guest no longer on the stay, with no audit trail explaining the discrepancy.
--
-- These two columns let CityTaxAssessmentService#rectifyForGuestRemoved void exactly the
-- one billing-service charge that guest's addition caused, and correct the assessment's
-- running total by exactly that amount — never touching the stay's original, immutable
-- check-in assessment, and never guessing at an amount to reverse.

ALTER TABLE stay_guests
    ADD COLUMN city_tax_charge_id     UUID,
    ADD COLUMN city_tax_charge_amount NUMERIC(12, 2);

COMMENT ON COLUMN stay_guests.city_tax_charge_id IS
    'billing-service charge id for this guest''s own tourist-tax contribution when added mid-stay — NULL for a guest present at check-in (part of the stay''s original assessment) or when no charge was actually posted.';
COMMENT ON COLUMN stay_guests.city_tax_charge_amount IS
    'Amount charged under city_tax_charge_id, kept alongside it so the assessment total can be corrected even if the billing-service reversal itself fails or the invoice has since closed.';
