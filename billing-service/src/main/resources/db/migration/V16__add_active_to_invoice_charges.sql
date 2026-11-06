-- GAP-18 follow-up: soft delete for invoice_charges.
--
-- A charge row is fiscal data (it feeds the FatturaPA/PDF lines and the VAT breakdown), yet
-- InvoiceServiceImpl.removeCharge issued a physical DELETE. Same pattern as invoices/payments
-- (V1): active column + @SQLDelete / @SQLRestriction. Existing rows stay visible (DEFAULT TRUE).

ALTER TABLE invoice_charges
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN invoice_charges.active IS 'Soft-delete flag. FALSE = logically deleted; filtered by @SQLRestriction.';
