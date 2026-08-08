-- ============================================================
-- Flyway migration: V9__add_rate_seasons.sql
-- Service  : frontdesk-service
-- Purpose  : Seasonal pricing per room type. Additive only — room_types.base_price
--            is untouched and remains the fallback price for any date not covered
--            by a rate_seasons row. Zero rows at deploy time = identical behavior
--            to today (see RatePricingServiceImpl).
--
-- btree_gist is required for the EXCLUDE constraint below: it supplies the
-- equality operator class needed to combine a UUID column with a daterange
-- overlap check inside a single GiST index. This is what prevents two
-- overlapping seasons from ever being stored for the same room type — an
-- application-level check alone would leave a race window between the read
-- and the write.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS rate_seasons (
    id             UUID           NOT NULL DEFAULT gen_random_uuid(),
    hotel_id       UUID           NOT NULL,
    room_type_id   UUID           NOT NULL,
    name           VARCHAR(100),
    start_date     DATE           NOT NULL,
    end_date       DATE           NOT NULL,
    nightly_price  NUMERIC(10, 2) NOT NULL,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP      NOT NULL,
    updated_at     TIMESTAMP      NOT NULL,

    CONSTRAINT pk_rate_seasons           PRIMARY KEY (id),
    CONSTRAINT fk_rate_seasons_room_type FOREIGN KEY (room_type_id)
                                             REFERENCES room_types (id)
                                             ON UPDATE CASCADE
                                             ON DELETE CASCADE,
    CONSTRAINT chk_rate_seasons_dates    CHECK (end_date >= start_date),
    CONSTRAINT chk_rate_seasons_price    CHECK (nightly_price >= 0),

    -- No two active seasons for the same room type may cover the same date.
    -- '[]' = inclusive on both ends, matching chk_rate_seasons_dates above.
    CONSTRAINT excl_rate_seasons_no_overlap
        EXCLUDE USING gist (
            room_type_id WITH =,
            daterange(start_date, end_date, '[]') WITH &&
        ) WHERE (active)
);

COMMENT ON TABLE  rate_seasons               IS 'Date-range price overrides per room type. Falls back to room_types.base_price for uncovered dates.';
COMMENT ON COLUMN rate_seasons.hotel_id      IS 'Hotel scope for multi-tenant isolation, same convention as room_types.hotel_id. Set server-side; never accepted from client.';
COMMENT ON COLUMN rate_seasons.name          IS 'Optional display label (e.g. "Alta stagione"). Not used in price resolution.';
COMMENT ON COLUMN rate_seasons.nightly_price IS 'Price per night for room_type_id during [start_date, end_date], inclusive.';
COMMENT ON COLUMN rate_seasons.active        IS 'Soft-delete flag. False = logically deleted; filtered by @SQLRestriction.';

CREATE INDEX IF NOT EXISTS idx_rate_seasons_room_type_dates
    ON rate_seasons (room_type_id, start_date, end_date)
    WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_rate_seasons_hotel_id
    ON rate_seasons (hotel_id)
    WHERE active = TRUE;
