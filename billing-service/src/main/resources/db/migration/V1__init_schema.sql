-- ============================================================
-- Flyway migration: V1__init_schema.sql
-- Service       : billing-service
-- Schema owner  : JPA entities Invoice and Payment
-- Soft-delete   : active column + @SQLDelete / @SQLRestriction
-- Auditing      : created_at / updated_at via Spring Data @CreatedDate
-- UUID strategy : gen_random_uuid() (pg >= 13, no extension needed)
-- Enums         : InvoiceStatus → ISSUED, PAID, CANCELLED
--                 PaymentMethod → CREDIT_CARD, CASH, TRANSFER
-- Cross-service : reservation_id and guest_id are logical refs (no DB FK)
-- ============================================================

-- ---------------------------------------------------------------
-- invoices
-- Maps to: @Entity @Table(name = "invoices") Invoice.java
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS invoices (
    id             UUID           NOT NULL DEFAULT gen_random_uuid(),
    hotel_id       UUID,
    invoice_number VARCHAR(255)   NOT NULL,
    issue_date     TIMESTAMP      NOT NULL,
    total_amount   NUMERIC(12, 2) NOT NULL,
    status         VARCHAR(30)    NOT NULL,
    reservation_id UUID           NOT NULL,
    guest_id       UUID           NOT NULL,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP      NOT NULL,
    updated_at     TIMESTAMP      NOT NULL,

    CONSTRAINT pk_invoices                  PRIMARY KEY (id),
    CONSTRAINT uq_invoices_invoice_number   UNIQUE (invoice_number),
    CONSTRAINT chk_invoices_status          CHECK (status IN (
        'ISSUED', 'PAID', 'CANCELLED'
    )),
    CONSTRAINT chk_invoices_total_amount    CHECK (total_amount >= 0)
);

COMMENT ON TABLE  invoices                IS 'Financial invoices generated per reservation/stay.';
COMMENT ON COLUMN invoices.id             IS 'Surrogate UUID primary key, generated server-side.';
COMMENT ON COLUMN invoices.hotel_id       IS 'Optional hotel identifier for multi-property deployments.';
COMMENT ON COLUMN invoices.invoice_number IS 'Human-readable, unique invoice reference (e.g. INV-2024-00001).';
COMMENT ON COLUMN invoices.issue_date     IS 'Date and time the invoice was issued.';
COMMENT ON COLUMN invoices.total_amount   IS 'Grand total amount due on this invoice.';
COMMENT ON COLUMN invoices.status         IS 'Invoice lifecycle: ISSUED | PAID | CANCELLED.';
COMMENT ON COLUMN invoices.reservation_id IS 'Logical reference to a reservation in reservation-service (cross-service, no DB FK).';
COMMENT ON COLUMN invoices.guest_id       IS 'Logical reference to a guest in guest-service (cross-service, no DB FK).';
COMMENT ON COLUMN invoices.active         IS 'Soft-delete flag. FALSE = logically deleted; filtered by @SQLRestriction.';
COMMENT ON COLUMN invoices.created_at     IS 'Record creation timestamp, managed by Spring Data Auditing.';
COMMENT ON COLUMN invoices.updated_at     IS 'Last modification timestamp, managed by Spring Data Auditing.';

-- Partial index mirroring @SQLRestriction("active = true")
CREATE INDEX IF NOT EXISTS idx_invoices_active
    ON invoices (active)
    WHERE active = TRUE;

-- Index for status-based financial queries (e.g., outstanding invoices)
CREATE INDEX IF NOT EXISTS idx_invoices_status
    ON invoices (status)
    WHERE active = TRUE;

-- Index for cross-service lookup: all invoices for a reservation
CREATE INDEX IF NOT EXISTS idx_invoices_reservation_id
    ON invoices (reservation_id)
    WHERE active = TRUE;

-- Index for cross-service lookup: all invoices for a guest
CREATE INDEX IF NOT EXISTS idx_invoices_guest_id
    ON invoices (guest_id)
    WHERE active = TRUE;

-- Index for multi-tenancy queries
CREATE INDEX IF NOT EXISTS idx_invoices_hotel_id
    ON invoices (hotel_id)
    WHERE active = TRUE;

-- ---------------------------------------------------------------
-- payments
-- Maps to: @Entity @Table(name = "payments") Payment.java
-- Enum    : PaymentMethod → CREDIT_CARD | CASH | TRANSFER
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    invoice_id            UUID           NOT NULL,
    payment_date          TIMESTAMP      NOT NULL,
    amount                NUMERIC(12, 2) NOT NULL,
    payment_method        VARCHAR(20)    NOT NULL,
    transaction_reference VARCHAR(255)   NOT NULL,
    active                BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP      NOT NULL,
    updated_at            TIMESTAMP      NOT NULL,

    CONSTRAINT pk_payments                  PRIMARY KEY (id),
    CONSTRAINT fk_payments_invoice          FOREIGN KEY (invoice_id)
                                                REFERENCES invoices (id)
                                                ON UPDATE CASCADE
                                                ON DELETE CASCADE,
    CONSTRAINT chk_payments_method         CHECK (payment_method IN (
        'CREDIT_CARD', 'CASH', 'TRANSFER'
    )),
    CONSTRAINT chk_payments_amount         CHECK (amount > 0)
);

COMMENT ON TABLE  payments                       IS 'Individual payment transactions applied against an invoice.';
COMMENT ON COLUMN payments.id                    IS 'Surrogate UUID primary key, generated server-side.';
COMMENT ON COLUMN payments.invoice_id            IS 'FK → invoices.id; the invoice this payment settles (partially or fully).';
COMMENT ON COLUMN payments.payment_date          IS 'Timestamp of the payment transaction.';
COMMENT ON COLUMN payments.amount                IS 'Amount paid in this transaction. Must be positive.';
COMMENT ON COLUMN payments.payment_method        IS 'Method used: CREDIT_CARD | CASH | TRANSFER.';
COMMENT ON COLUMN payments.transaction_reference IS 'External reference (card authorization code, bank ref, receipt #).';
COMMENT ON COLUMN payments.active                IS 'Soft-delete flag. FALSE = logically deleted; filtered by @SQLRestriction.';
COMMENT ON COLUMN payments.created_at            IS 'Record creation timestamp, managed by Spring Data Auditing.';
COMMENT ON COLUMN payments.updated_at            IS 'Last modification timestamp, managed by Spring Data Auditing.';

-- Partial index mirroring @SQLRestriction("active = true")
CREATE INDEX IF NOT EXISTS idx_payments_active
    ON payments (active)
    WHERE active = TRUE;

-- Index on FK to accelerate invoice → payments JOIN
CREATE INDEX IF NOT EXISTS idx_payments_invoice_id
    ON payments (invoice_id);

-- Index for payment-method reporting
CREATE INDEX IF NOT EXISTS idx_payments_method
    ON payments (payment_method)
    WHERE active = TRUE;
