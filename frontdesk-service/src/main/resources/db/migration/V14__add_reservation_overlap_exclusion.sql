-- ============================================================
-- Flyway migration: V14__add_reservation_overlap_exclusion.sql
-- Service  : frontdesk-service
-- Purpose  : DB-level exclusion constraint against double-booking (Finding #5,
--            security-report.md — HIGH). findOverlappingReservationsForNew's
--            PESSIMISTIC_WRITE lock only takes effect on rows the SELECT
--            actually returns — for a room/date range with no prior
--            reservation, two concurrent inserts both see an empty result
--            and both pass. Same class of gap rate_seasons closed with
--            EXCLUDE USING gist (V9); this is the same fix for reservations.
--
-- reservation_line_items doesn't have its own dates/status (those live on the
-- parent reservations row) so an EXCLUDE constraint here needs them
-- denormalized onto this table — kept in sync by trigger, not by application
-- code, so no future write path can silently drift out of sync.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS btree_gist; -- idempotent, already enabled by V9 in this DB

ALTER TABLE reservation_line_items
    ADD COLUMN check_in_date DATE,
    ADD COLUMN check_out_date DATE,
    ADD COLUMN booking_blocking BOOLEAN NOT NULL DEFAULT TRUE;

-- Backfill from existing rows
UPDATE reservation_line_items li
SET    check_in_date    = r.check_in_date,
       check_out_date   = r.check_out_date,
       booking_blocking = (r.status NOT IN ('CANCELLED', 'NO_SHOW'))
FROM   reservations r
WHERE  r.id = li.reservation_id;

ALTER TABLE reservation_line_items
    ALTER COLUMN check_in_date  SET NOT NULL,
    ALTER COLUMN check_out_date SET NOT NULL;

COMMENT ON COLUMN reservation_line_items.check_in_date IS 'Denormalized from reservations.check_in_date via trigger — required for the EXCLUDE constraint below, which can only see columns on this table.';
COMMENT ON COLUMN reservation_line_items.check_out_date IS 'Denormalized from reservations.check_out_date via trigger. See check_in_date.';
COMMENT ON COLUMN reservation_line_items.booking_blocking IS 'Denormalized from (reservations.status NOT IN (CANCELLED, NO_SHOW)) via trigger. False = does not block room availability.';

CREATE OR REPLACE FUNCTION sync_reservation_line_item_booking_fields() RETURNS trigger AS $$
BEGIN
    SELECT check_in_date, check_out_date, status NOT IN ('CANCELLED', 'NO_SHOW')
    INTO   NEW.check_in_date, NEW.check_out_date, NEW.booking_blocking
    FROM   reservations WHERE id = NEW.reservation_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sync_reservation_line_item_booking_fields
    BEFORE INSERT OR UPDATE ON reservation_line_items
    FOR EACH ROW EXECUTE FUNCTION sync_reservation_line_item_booking_fields();

CREATE OR REPLACE FUNCTION sync_children_on_reservation_change() RETURNS trigger AS $$
BEGIN
    IF NEW.check_in_date IS DISTINCT FROM OLD.check_in_date
       OR NEW.check_out_date IS DISTINCT FROM OLD.check_out_date
       OR NEW.status IS DISTINCT FROM OLD.status THEN
        UPDATE reservation_line_items SET id = id WHERE reservation_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sync_children_on_reservation_change
    AFTER UPDATE ON reservations
    FOR EACH ROW EXECUTE FUNCTION sync_children_on_reservation_change();

-- No two active, booking-blocking line items may cover the same room on
-- overlapping dates. daterange() defaults to '[)' — semi-open — matching the
-- existing application check (checkInDate < :checkOut AND checkOutDate > :checkIn).
ALTER TABLE reservation_line_items
    ADD CONSTRAINT excl_reservation_line_items_no_overlap
        EXCLUDE USING gist (
            room_id WITH =,
            daterange(check_in_date, check_out_date) WITH &&
        ) WHERE (active AND booking_blocking);
