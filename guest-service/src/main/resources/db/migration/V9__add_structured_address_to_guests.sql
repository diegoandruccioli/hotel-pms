-- P0-1: indirizzo strutturato italiano (CAP/Comune/Provincia) per la Sede del
-- cessionario nell'XML FatturaPA. Campi opzionali, distinti da city/country
-- (testo libero, usati anche per ospiti esteri) — usati solo quando il guest
-- è cessionario di una fattura italiana.
ALTER TABLE guests
    ADD COLUMN cap       VARCHAR(5)  NULL,
    ADD COLUMN comune    VARCHAR(100) NULL,
    ADD COLUMN provincia VARCHAR(2)  NULL;
