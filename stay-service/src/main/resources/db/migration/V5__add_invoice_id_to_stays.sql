-- ============================================================
-- Flyway migration: V5__add_invoice_id_to_stays.sql
-- Service       : stay-service
-- Change        : add invoice_id to stays for F&B → Conto Camera
-- invoice_id is set after a successful check-in when billing-service
-- creates the invoice folio. Nullable: legacy stays have no invoice_id.
-- ============================================================

ALTER TABLE stays ADD COLUMN IF NOT EXISTS invoice_id UUID;

COMMENT ON COLUMN stays.invoice_id IS 'Logical reference to the billing invoice opened at check-in (cross-service, no DB FK).';
