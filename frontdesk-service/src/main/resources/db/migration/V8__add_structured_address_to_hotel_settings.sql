-- P0-1: indirizzo strutturato per l'XML FatturaPA (Sede/CAP/Comune/Provincia).
-- Campi opzionali per retrocompatibilità: hotel esistenti restano validi finché
-- non provano a esportare un XML FatturaPA (billing-service blocca l'export,
-- non la lettura, se mancano). "address" resta per la sola via/civico.
ALTER TABLE hotel_settings
    ADD COLUMN cap       VARCHAR(5),
    ADD COLUMN comune    VARCHAR(100),
    ADD COLUMN provincia VARCHAR(2);
