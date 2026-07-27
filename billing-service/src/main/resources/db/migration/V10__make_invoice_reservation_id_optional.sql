-- reservation_id is a logical cross-service reference to a reservation, but walk-in
-- stays (checked in without a prior reservation) legitimately have none — the
-- application layer already treats this as a real case (BillingClient's javadoc
-- documents invoiceId-based lookup for exactly this scenario), but the NOT NULL
-- constraint here was missed, causing invoice creation to fail with a 400 for
-- every walk-in check-in.
ALTER TABLE invoices
    ALTER COLUMN reservation_id DROP NOT NULL;
