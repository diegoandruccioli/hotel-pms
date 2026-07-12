-- Extends payment_method values: adds DEBIT_CARD, CHECK; renames TRANSFER → BANK_TRANSFER.
ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payments_method;

UPDATE payments SET payment_method = 'BANK_TRANSFER' WHERE payment_method = 'TRANSFER';

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_method CHECK (payment_method IN (
        'CREDIT_CARD', 'DEBIT_CARD', 'CASH', 'BANK_TRANSFER', 'CHECK'
    ));

COMMENT ON COLUMN payments.payment_method IS 'Method used: CREDIT_CARD | DEBIT_CARD | CASH | BANK_TRANSFER | CHECK.';
