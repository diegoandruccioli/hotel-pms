-- ============================================================
-- Flyway migration: V12__add_unit_price_and_nights_to_charges.sql
-- Service  : billing-service
-- Purpose  : Optional per-night breakdown metadata for ROOM_NIGHT charges,
--            populated by frontdesk-service's RatePricingService (seasonal
--            pricing). Deliberately additive and nullable: invoice_charges.amount
--            remains the sole fiscally authoritative field, unchanged, still the
--            only value VatBreakdownCalculator/FatturaPAServiceImpl reconcile
--            against. unit_price/nights are display/audit metadata only — null
--            whenever the caller doesn't have a single uniform per-night rate to
--            report (e.g. F&B charges from fb-service, or a room-night charge
--            whose nights crossed a rate-season boundary).
-- ============================================================

ALTER TABLE invoice_charges
    ADD COLUMN IF NOT EXISTS unit_price NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS nights     INTEGER;

ALTER TABLE invoice_charges
    ADD CONSTRAINT chk_invoice_charges_nights CHECK (nights IS NULL OR nights > 0);

COMMENT ON COLUMN invoice_charges.unit_price IS 'Optional per-night price, display/audit only. NULL when no single uniform rate applies. Never used to derive amount.';
COMMENT ON COLUMN invoice_charges.nights     IS 'Optional number of nights this charge covers, display/audit only.';
