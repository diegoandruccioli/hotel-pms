-- E18 prep: a stable comune identifier on hotel_settings.
--
-- HotelSettings.comune/provincia are validated against alloggiati_comuni (the
-- Polizia di Stato 9-digit reference table, already populated at startup by
-- AlloggiatiLookupDataLoader) but only as a name/provincia existence check —
-- the code itself was never stored. A versioned tourist-tax-rate table needs
-- a stable key, and alloggiati_comuni.codice is already the correct one to
-- reuse (it is NOT the ISTAT code and NOT the Belfiore code — a third,
-- distinct numbering used only by the Portale Alloggiati Web feed — but it is
-- already the source of truth this codebase validates the comune name against,
-- so reusing it avoids introducing a second, redundant comune-code system).

ALTER TABLE hotel_settings
    ADD COLUMN comune_codice VARCHAR(9);

ALTER TABLE hotel_settings
    ADD CONSTRAINT fk_hotel_settings_comune FOREIGN KEY (comune_codice)
        REFERENCES alloggiati_comuni (codice);

-- Best-effort backfill for hotels that already have a validated comune/provincia
-- pair on file. Ambiguous or already-expired comuni are left NULL rather than
-- guessed — HotelSettingsServiceImpl.update() resolves and stores the code
-- going forward for every write from here on.
UPDATE hotel_settings hs
SET comune_codice = (
    SELECT c.codice FROM alloggiati_comuni c
    WHERE LOWER(c.descrizione) = LOWER(hs.comune)
      AND c.provincia = hs.provincia
      AND (c.data_fine_val IS NULL OR c.data_fine_val > CURRENT_DATE)
    ORDER BY c.codice
    LIMIT 1
)
WHERE hs.comune IS NOT NULL AND hs.provincia IS NOT NULL;

COMMENT ON COLUMN hotel_settings.comune_codice IS
    'FK -> alloggiati_comuni.codice (Polizia di Stato 9-digit code, not ISTAT/Belfiore). Stable key for city_tax_rates; comune/provincia above remain the human-readable, independently-validated pair.';
