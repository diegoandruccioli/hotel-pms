-- transaction_reference is meaningful for card/transfer payments, not for cash;
-- the application layer already treats it as optional (PaymentRequest), but the
-- NOT NULL constraint here was missed, causing cash payments to fail at insert.
ALTER TABLE payments
    ALTER COLUMN transaction_reference DROP NOT NULL;
