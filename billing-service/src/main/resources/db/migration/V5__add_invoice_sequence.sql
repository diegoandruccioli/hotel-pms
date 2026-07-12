-- ============================================================
-- Flyway migration: V5__add_invoice_sequence.sql
-- Service       : billing-service
-- Purpose       : Numerazione fattura progressiva per anno solare (D.P.R. 633/72 art. 21)
--                 Sostituisce UUID random con formato YYYY/NNNN per hotel+anno.
-- ============================================================

-- Tabella contatori per hotel + anno solare
CREATE TABLE invoice_sequences (
    hotel_id  UUID    NOT NULL,
    year      INTEGER NOT NULL,
    last_seq  BIGINT  NOT NULL DEFAULT 0,
    CONSTRAINT pk_invoice_sequences PRIMARY KEY (hotel_id, year),
    CONSTRAINT chk_invoice_sequences_year    CHECK (year >= 2000),
    CONSTRAINT chk_invoice_sequences_lastseq CHECK (last_seq >= 0)
);

COMMENT ON TABLE  invoice_sequences          IS 'Contatori per numerazione fattura progressiva per anno solare e hotel.';
COMMENT ON COLUMN invoice_sequences.hotel_id IS 'Hotel tenant. Ogni hotel ha la propria sequenza indipendente.';
COMMENT ON COLUMN invoice_sequences.year     IS 'Anno solare (es. 2026). La sequenza riparte da 0 ogni anno.';
COMMENT ON COLUMN invoice_sequences.last_seq IS 'Ultimo numero di sequenza emesso. Incrementato atomicamente con PESSIMISTIC_WRITE.';

-- Rimuove il vincolo di unicità globale sul numero fattura:
-- con formato YYYY/NNNN due hotel diversi producono entrambi 2026/0001.
-- Il nuovo vincolo garantisce unicità per (hotel, numero).
ALTER TABLE invoices DROP CONSTRAINT uq_invoices_invoice_number;
ALTER TABLE invoices ADD CONSTRAINT uq_invoices_hotel_invoice_number UNIQUE (hotel_id, invoice_number);
