-- ============================================================
-- Flyway migration: V11__add_invoice_fiscal_exports.sql
-- Service       : billing-service
-- Schema owner  : JPA entity InvoiceFiscalExport
-- Purpose       : Immutable record of every FatturaPA XML actually produced and
--                 handed off, so a later regeneration can never be silently
--                 assumed identical to what was historically exported. No FK
--                 to a transmission provider — hotel-pms does not send to SDI,
--                 it produces validated exports for the commercialista/
--                 third-party software to import.
-- ============================================================

CREATE TABLE IF NOT EXISTS invoice_fiscal_exports (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    invoice_id     UUID         NOT NULL,
    hotel_id       UUID,
    exported_at    TIMESTAMP    NOT NULL,
    xml_payload    BYTEA        NOT NULL,
    payload_sha256 VARCHAR(64)  NOT NULL,
    exported_by    VARCHAR(255) NOT NULL,
    created_at     TIMESTAMP    NOT NULL,

    CONSTRAINT pk_invoice_fiscal_exports     PRIMARY KEY (id),
    CONSTRAINT fk_invoice_fiscal_exports_invoice FOREIGN KEY (invoice_id)
                                                  REFERENCES invoices (id)
                                                  ON UPDATE CASCADE
                                                  ON DELETE CASCADE
);

COMMENT ON TABLE  invoice_fiscal_exports                IS 'Immutable snapshot of every FatturaPA XML export generated for an invoice — exact bytes + hash + timestamp, independent of later regenerations.';
COMMENT ON COLUMN invoice_fiscal_exports.id              IS 'Surrogate UUID primary key, generated server-side.';
COMMENT ON COLUMN invoice_fiscal_exports.invoice_id      IS 'FK -> invoices.id; the invoice this export was generated from.';
COMMENT ON COLUMN invoice_fiscal_exports.hotel_id        IS 'Denormalized tenant scope, mirrors invoices.hotel_id, for direct multi-tenant queries without a join.';
COMMENT ON COLUMN invoice_fiscal_exports.exported_at     IS 'Timestamp the export was generated and handed off.';
COMMENT ON COLUMN invoice_fiscal_exports.xml_payload     IS 'Exact FatturaPA XML bytes produced for this export — the legal record of what was actually delivered.';
COMMENT ON COLUMN invoice_fiscal_exports.payload_sha256  IS 'SHA-256 hex digest of xml_payload, for fast integrity checks without reading the full BYTEA.';
COMMENT ON COLUMN invoice_fiscal_exports.exported_by     IS 'Username/principal that triggered the export.';
COMMENT ON COLUMN invoice_fiscal_exports.created_at      IS 'Record creation timestamp, managed by Spring Data Auditing.';

-- Existence check drives the post-export immutability guard (InvoiceServiceImpl):
-- once a row exists for an invoice_id, fiscally-relevant fields are locked.
CREATE INDEX IF NOT EXISTS idx_invoice_fiscal_exports_invoice_id
    ON invoice_fiscal_exports (invoice_id);

-- Index for hotel-scoped batch export queries (GET /invoices/export?from=&to=&hotelId=)
CREATE INDEX IF NOT EXISTS idx_invoice_fiscal_exports_hotel_id
    ON invoice_fiscal_exports (hotel_id);
