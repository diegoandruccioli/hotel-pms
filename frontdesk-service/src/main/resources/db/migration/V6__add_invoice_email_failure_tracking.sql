-- Mirrors the Alloggiati failure-tracking pattern (V3) for the other two
-- fragile cross-service calls found in the outbox-mirato analysis: invoice
-- creation at check-in (billing-service) and transactional emails
-- (notification-service). Both already have a resilience4j circuit-breaker
-- fallback that swallows the failure silently — these columns make the
-- failure durable and give staff a manual retry path, same as Alloggiati.
ALTER TABLE stays
    ADD COLUMN invoice_creation_failed BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN invoice_creation_failure_reason VARCHAR(500),
    ADD COLUMN checkout_email_failed BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN checkout_email_failure_reason VARCHAR(500);

ALTER TABLE reservations
    ADD COLUMN confirmation_email_failed BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN confirmation_email_failure_reason VARCHAR(500);
