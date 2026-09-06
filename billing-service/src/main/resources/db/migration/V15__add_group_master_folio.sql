-- Group management (pilot plan, Punto 4): master folio support. An invoice can now
-- be a MASTER folio for a reservation group (frontdesk-service) instead of an
-- INDIVIDUAL one for a single stay -- stay_id stays NULL for a MASTER invoice,
-- exactly like it already does for any invoice not yet linked to a stay.

ALTER TABLE invoices
    ADD COLUMN group_id UUID,
    ADD COLUMN folio_type VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL';

ALTER TABLE invoices
    ADD CONSTRAINT chk_invoices_folio_type CHECK (folio_type IN ('INDIVIDUAL', 'MASTER'));

COMMENT ON COLUMN invoices.group_id   IS 'Logical reference to a reservation group in frontdesk-service (cross-service, no DB FK). NULL for an individual-stay invoice.';
COMMENT ON COLUMN invoices.folio_type IS 'INDIVIDUAL (default, tied to one stay) | MASTER (tied to a reservation group, group_id set).';

CREATE INDEX IF NOT EXISTS idx_invoices_group_id
    ON invoices (group_id)
    WHERE group_id IS NOT NULL;

-- Tracks which stay a charge was originally posted for, when that charge is later
-- transferred from an individual folio to its group's master folio (a room marked
-- "billed to group" at check-in still opens its own individual invoice first --
-- unchanged, lowest-risk path -- and the ROOM_NIGHT/CITY_TAX charges are moved to
-- the master folio at that room's check-out instead). NULL for every charge that
-- was posted directly and never transferred.
ALTER TABLE invoice_charges
    ADD COLUMN routed_from_stay_id UUID;

COMMENT ON COLUMN invoice_charges.routed_from_stay_id IS 'Stay a transferred charge originally belonged to (cross-service, no DB FK). NULL unless this charge was moved from an individual folio to a group master folio.';
