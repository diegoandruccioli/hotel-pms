-- E12: aliquota IVA per riga addebito (D.P.R. 633/72)
-- ROOM_NIGHT e FB_ORDER → 10% (Tab. A, Parte III)
-- EXTRA → 22% (aliquota ordinaria)

ALTER TABLE invoice_charges
    ADD COLUMN vat_rate NUMERIC(5,4);

UPDATE invoice_charges
SET vat_rate = CASE
    WHEN type = 'EXTRA' THEN 0.2200
    ELSE 0.1000
END;

ALTER TABLE invoice_charges
    ALTER COLUMN vat_rate SET NOT NULL;
