-- ============================================================
-- Flyway migration: V8__add_hotel_profile_fields.sql
-- Service       : stay-service
-- Adds hotel profile fields to hotel_settings for display on
-- invoices and in the /profile/hotel frontend page.
-- All columns are nullable: existing rows keep NULL until the
-- hotel admin fills in the profile form.
-- ============================================================

ALTER TABLE hotel_settings
    ADD COLUMN IF NOT EXISTS hotel_name  VARCHAR(150),
    ADD COLUMN IF NOT EXISTS address     VARCHAR(200),
    ADD COLUMN IF NOT EXISTS vat_number  VARCHAR(20),
    ADD COLUMN IF NOT EXISTS fiscal_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS logo_url    VARCHAR(500);

COMMENT ON COLUMN hotel_settings.hotel_name  IS 'Display name of the hotel property.';
COMMENT ON COLUMN hotel_settings.address     IS 'Full street address including civic number.';
COMMENT ON COLUMN hotel_settings.vat_number  IS 'Partita IVA (Italian VAT number, 11 digits).';
COMMENT ON COLUMN hotel_settings.fiscal_code IS 'Codice Fiscale (Italian fiscal code).';
COMMENT ON COLUMN hotel_settings.logo_url    IS 'Optional URL of the hotel logo image.';
