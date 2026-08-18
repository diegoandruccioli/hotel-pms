-- E18: allow CITY_TAX (imposta di soggiorno) as a valid invoice_charges.type value.
-- Out-of-VAT-scope representation (natura_code, chk_charges_natura_requires_zero_rate)
-- already added by V13 — this migration only extends the type CHECK constraint.

ALTER TABLE invoice_charges
    DROP CONSTRAINT chk_charges_type;

ALTER TABLE invoice_charges
    ADD CONSTRAINT chk_charges_type CHECK (type IN ('ROOM_NIGHT', 'FB_ORDER', 'EXTRA', 'CITY_TAX'));

COMMENT ON COLUMN invoice_charges.type IS 'Charge category: ROOM_NIGHT | FB_ORDER | EXTRA | CITY_TAX.';
