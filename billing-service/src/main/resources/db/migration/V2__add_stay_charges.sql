-- ============================================================
-- Flyway migration: V2__add_stay_charges.sql
-- Service       : billing-service
-- Changes       : (1) stay_id on invoices for F&B → Conto Camera
--                 (2) invoice_charges for per-item billing (room night, F&B, extra)
-- ============================================================

-- ---------------------------------------------------------------
-- Add stay_id to invoices
-- Nullable: existing invoices (manually created) have no stay_id.
-- ---------------------------------------------------------------
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS stay_id UUID;

CREATE INDEX IF NOT EXISTS idx_invoices_stay_id
    ON invoices (stay_id)
    WHERE active = TRUE;

-- ---------------------------------------------------------------
-- invoice_charges
-- One row per billable item: room night, F&B order, extra service.
-- totalAmount on invoices is kept in sync by the application layer.
-- No soft-delete: charges are immutable once added.
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS invoice_charges (
    id           UUID          NOT NULL DEFAULT gen_random_uuid(),
    invoice_id   UUID          NOT NULL,
    type         VARCHAR(20)   NOT NULL,
    description  VARCHAR(255)  NOT NULL,
    amount       NUMERIC(10,2) NOT NULL,
    reference_id UUID,
    created_at   TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT pk_invoice_charges       PRIMARY KEY (id),
    CONSTRAINT fk_charges_invoice       FOREIGN KEY (invoice_id)
                                            REFERENCES invoices (id)
                                            ON UPDATE CASCADE
                                            ON DELETE CASCADE,
    CONSTRAINT chk_charges_type         CHECK (type IN ('ROOM_NIGHT', 'FB_ORDER', 'EXTRA')),
    CONSTRAINT chk_charges_amount       CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_charges_invoice_id
    ON invoice_charges (invoice_id);

COMMENT ON TABLE  invoice_charges              IS 'Line-item charges added to an invoice (room nights, F&B orders, extras).';
COMMENT ON COLUMN invoice_charges.id           IS 'Surrogate UUID primary key.';
COMMENT ON COLUMN invoice_charges.invoice_id   IS 'FK → invoices.id; the invoice this charge belongs to.';
COMMENT ON COLUMN invoice_charges.type         IS 'Charge category: ROOM_NIGHT | FB_ORDER | EXTRA.';
COMMENT ON COLUMN invoice_charges.description  IS 'Human-readable description of the charge (e.g. "Espresso x2").';
COMMENT ON COLUMN invoice_charges.amount       IS 'Charge amount. Must be non-negative.';
COMMENT ON COLUMN invoice_charges.reference_id IS 'Optional logical reference (e.g. stay_id, order_id) — cross-service, no DB FK.';
COMMENT ON COLUMN invoice_charges.created_at   IS 'Timestamp when the charge was recorded.';
