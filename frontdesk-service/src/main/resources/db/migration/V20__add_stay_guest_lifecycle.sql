-- Parte 1: a guest attached to a stay was immutable after check-in — no way to
-- add a late arrival, record an early departure, or correct a rejected
-- document. That's not just an operator inconvenience: it's an Alloggiati Web
-- (art. 109 TULPS) compliance defect. The report is due within 24h of each
-- GUEST's arrival, not the room's check-in — a guest joining on day 3 must be
-- reported in day 3's file, not silently absorbed into (or missing from) the
-- stay's original check-in day file.
--
-- arrival_date backfills to the stay's own check-in date for every existing
-- guest — correct by construction, since today every guest arrives with the
-- room. alloggiati_sent/alloggiati_sent_at/needs_resubmit move the "was this
-- guest's schedina actually transmitted" bookkeeping from Stay (one flag for
-- the whole room) down to StayGuest (one flag per person) — the only grain
-- Alloggiati Web actually recognizes. version supports the same optimistic
-- locking already used on Reservation, now that this row is mutable after
-- creation instead of a write-once check-in snapshot.

ALTER TABLE stay_guests
    ADD COLUMN arrival_date        DATE,
    ADD COLUMN departure_date      DATE,
    ADD COLUMN alloggiati_sent     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN alloggiati_sent_at  TIMESTAMP,
    ADD COLUMN needs_resubmit      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN version             BIGINT  NOT NULL DEFAULT 0;

UPDATE stay_guests sg
   SET arrival_date    = COALESCE(s.actual_check_in_time::date, sg.created_at::date),
       alloggiati_sent = s.alloggiati_sent
  FROM stays s
 WHERE s.id = sg.stay_id;

ALTER TABLE stay_guests ALTER COLUMN arrival_date SET NOT NULL;

ALTER TABLE stay_guests
    ADD CONSTRAINT chk_stay_guests_dates
        CHECK (departure_date IS NULL OR departure_date >= arrival_date);

COMMENT ON COLUMN stay_guests.arrival_date       IS 'The date this specific guest arrived — drives which Alloggiati Web daily file they belong to, distinct from the stay/room check-in date.';
COMMENT ON COLUMN stay_guests.needs_resubmit     IS 'Set when a guest already sent to Alloggiati Web is corrected afterward. Alloggiati Web has no rectification API: the fix is a full resubmission, tracked here until the next report run picks it up regardless of its arrival_date.';

CREATE INDEX IF NOT EXISTS idx_stay_guests_arrival_date ON stay_guests (arrival_date);
CREATE INDEX IF NOT EXISTS idx_stay_guests_needs_resubmit ON stay_guests (needs_resubmit) WHERE needs_resubmit = TRUE;
