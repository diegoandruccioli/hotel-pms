-- ============================================================
-- Flyway migration: V1__init_schema.sql
-- Service       : reservation-service
-- Schema owner  : JPA entities Reservation and ReservationLineItem
-- Soft-delete   : active column + @SQLDelete / @SQLRestriction
-- Auditing      : created_at / updated_at via Spring Data @CreatedDate
-- UUID strategy : gen_random_uuid() (pg >= 13, no extension needed)
-- ============================================================

-- ---------------------------------------------------------------
-- reservations
-- Maps to: @Entity @Table(name = "reservations") Reservation.java
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reservations (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    hotel_id       UUID,
    guest_name     VARCHAR(100) NOT NULL,
    check_in_date  DATE         NOT NULL,
    check_out_date DATE         NOT NULL,
    status         VARCHAR(50)  NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,

    CONSTRAINT pk_reservations                PRIMARY KEY (id),
    CONSTRAINT chk_reservations_dates         CHECK (check_out_date > check_in_date),
    CONSTRAINT chk_reservations_status        CHECK (status IN (
        'PENDING', 'CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED', 'NO_SHOW'
    ))
);

COMMENT ON TABLE  reservations                IS 'Booking records linking a guest to one or more rooms over a date range.';
COMMENT ON COLUMN reservations.id             IS 'Surrogate UUID primary key, generated server-side.';
COMMENT ON COLUMN reservations.hotel_id       IS 'Optional hotel identifier for multi-property deployments.';
COMMENT ON COLUMN reservations.guest_name     IS 'Display name of the primary guest for the reservation.';
COMMENT ON COLUMN reservations.check_in_date  IS 'Planned arrival date (inclusive).';
COMMENT ON COLUMN reservations.check_out_date IS 'Planned departure date (exclusive).';
COMMENT ON COLUMN reservations.status         IS 'Lifecycle stage: PENDING|CONFIRMED|CHECKED_IN|CHECKED_OUT|CANCELLED|NO_SHOW.';
COMMENT ON COLUMN reservations.active         IS 'Soft-delete flag. FALSE = logically deleted; filtered by @SQLRestriction.';
COMMENT ON COLUMN reservations.created_at     IS 'Record creation timestamp, managed by Spring Data Auditing.';
COMMENT ON COLUMN reservations.updated_at     IS 'Last modification timestamp, managed by Spring Data Auditing.';

-- Partial index mirroring @SQLRestriction("active = true")
CREATE INDEX IF NOT EXISTS idx_reservations_active
    ON reservations (active)
    WHERE active = TRUE;

-- Operational index: find reservations containing a specific date range
CREATE INDEX IF NOT EXISTS idx_reservations_check_in_date
    ON reservations (check_in_date)
    WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_reservations_check_out_date
    ON reservations (check_out_date)
    WHERE active = TRUE;

-- Index for multi-tenancy queries
CREATE INDEX IF NOT EXISTS idx_reservations_hotel_id
    ON reservations (hotel_id)
    WHERE active = TRUE;

-- ---------------------------------------------------------------
-- reservation_line_items
-- Maps to: @Entity @Table(name = "reservation_line_items") ReservationLineItem.java
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reservation_line_items (
    id             UUID           NOT NULL DEFAULT gen_random_uuid(),
    reservation_id UUID           NOT NULL,
    room_id        UUID           NOT NULL,
    price          NUMERIC(10, 2) NOT NULL,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP      NOT NULL,
    updated_at     TIMESTAMP      NOT NULL,

    CONSTRAINT pk_reservation_line_items       PRIMARY KEY (id),
    CONSTRAINT fk_rli_reservation              FOREIGN KEY (reservation_id)
                                                   REFERENCES reservations (id)
                                                   ON UPDATE CASCADE
                                                   ON DELETE CASCADE,
    CONSTRAINT chk_rli_price                   CHECK (price >= 0)
);

COMMENT ON TABLE  reservation_line_items                IS 'Individual room assignments within a reservation.';
COMMENT ON COLUMN reservation_line_items.id             IS 'Surrogate UUID primary key, generated server-side.';
COMMENT ON COLUMN reservation_line_items.reservation_id IS 'FK → reservations.id; the parent reservation.';
COMMENT ON COLUMN reservation_line_items.room_id        IS 'Reference to the room in inventory-service (cross-service, no FK).';
COMMENT ON COLUMN reservation_line_items.price          IS 'Agreed nightly rate for this room within the reservation.';
COMMENT ON COLUMN reservation_line_items.active         IS 'Soft-delete flag. FALSE = logically deleted; filtered by @SQLRestriction.';
COMMENT ON COLUMN reservation_line_items.created_at     IS 'Record creation timestamp, managed by Spring Data Auditing.';
COMMENT ON COLUMN reservation_line_items.updated_at     IS 'Last modification timestamp, managed by Spring Data Auditing.';

-- Partial index mirroring @SQLRestriction("active = true")
CREATE INDEX IF NOT EXISTS idx_rli_active
    ON reservation_line_items (active)
    WHERE active = TRUE;

-- Index on FK to accelerate JOIN / orphan detection queries
CREATE INDEX IF NOT EXISTS idx_rli_reservation_id
    ON reservation_line_items (reservation_id);

-- Index to quickly find all line items for a given room
CREATE INDEX IF NOT EXISTS idx_rli_room_id
    ON reservation_line_items (room_id)
    WHERE active = TRUE;
