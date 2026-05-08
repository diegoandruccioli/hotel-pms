-- V7: Alloggiati Web lookup tables + schema fixes

-- Lookup table: Portale Alloggiati Web stati (countries)
CREATE TABLE alloggiati_stati (
    codice      VARCHAR(9)   NOT NULL,
    descrizione VARCHAR(100) NOT NULL,
    data_fine_val DATE,
    CONSTRAINT pk_alloggiati_stati PRIMARY KEY (codice)
);

-- Lookup table: Portale Alloggiati Web comuni (municipalities)
CREATE TABLE alloggiati_comuni (
    codice        VARCHAR(9)  NOT NULL,
    descrizione   VARCHAR(100) NOT NULL,
    provincia     VARCHAR(2)  NOT NULL,
    data_fine_val DATE,
    CONSTRAINT pk_alloggiati_comuni PRIMARY KEY (codice)
);
CREATE INDEX idx_alloggiati_comuni_provincia ON alloggiati_comuni (provincia);
CREATE INDEX idx_alloggiati_comuni_descrizione ON alloggiati_comuni (LOWER(descrizione) varchar_pattern_ops);

-- Lookup table: Portale Alloggiati Web tipo documento
CREATE TABLE alloggiati_tipdoc (
    codice      VARCHAR(5)   NOT NULL,
    descrizione VARCHAR(100) NOT NULL,
    CONSTRAINT pk_alloggiati_tipdoc PRIMARY KEY (codice)
);

-- Make document fields nullable for FAMILIARE/MEMBRO_GRUPPO (TIPALLOG 19/20)
ALTER TABLE stay_guests
    ALTER COLUMN document_type          DROP NOT NULL,
    ALTER COLUMN document_number        DROP NOT NULL,
    ALTER COLUMN document_place_of_issue DROP NOT NULL;

-- Add expected check-out date for permanenza calculation in the tracciato
ALTER TABLE stays
    ADD COLUMN expected_check_out_date DATE;
