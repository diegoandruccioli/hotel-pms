-- E18 (imposta di soggiorno) prep — Natura IVA per righe fuori campo/escluse IVA.
--
-- La tassa di soggiorno (art. 4 D.Lgs. 23/2011) non è imponibile IVA: il gestore la
-- riscuote in nome e per conto del Comune, non come corrispettivo di una prestazione
-- propria. Prima di questa migration non esisteva modo di rappresentarlo: vat_rate è
-- NUMERIC(5,4) NOT NULL, quindi un valore 0.0000 sarebbe stato trattato come
-- un'aliquota IVA 0% legittima — fiscalmente sbagliato, perché FatturaPA distingue
-- "aliquota 0%" da "escluso/non soggetto" tramite l'elemento <Natura>, mai emesso
-- finora da FatturaPAServiceImpl nonostante l'XSD lo supporti (Schema_VFPR12_v1.2.3.xsd,
-- NaturaType, minOccurs="0").
--
-- natura_code è nullable: resta NULL per ogni riga imponibile ordinaria (ROOM_NIGHT,
-- FB_ORDER, EXTRA) — comportamento invariato. Non ancora usato da nessun ChargeType
-- reale in questa migration: prepara solo il meccanismo, CITY_TAX arriva in una
-- migration successiva. Il vincolo impedisce lo stato incoerente "natura impostata ma
-- aliquota diversa da zero" — una riga non può essere sia imponibile che esclusa.

ALTER TABLE invoice_charges
    ADD COLUMN natura_code VARCHAR(4);

ALTER TABLE invoice_charges
    ADD CONSTRAINT chk_charges_natura_requires_zero_rate
        CHECK (natura_code IS NULL OR vat_rate = 0);

COMMENT ON COLUMN invoice_charges.natura_code IS
    'Codice Natura FatturaPA (es. N1 = escluse ex art. 15 DPR 633/1972) per righe fuori campo IVA. NULL = riga imponibile ordinaria, vat_rate applicata normalmente.';
