-- V3: campi fiscali per fatturazione (codice fiscale, P.IVA, SDI, PEC)
ALTER TABLE guests
    ADD COLUMN fiscal_code  VARCHAR(16)  NULL,
    ADD COLUMN vat_number   VARCHAR(20)  NULL,
    ADD COLUMN company_name VARCHAR(200) NULL,
    ADD COLUMN sdi_code     VARCHAR(7)   NULL,
    ADD COLUMN pec_email    VARCHAR(150) NULL;
