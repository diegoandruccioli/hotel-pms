-- ============================================================
-- Flyway migration: V1__init_schema.sql
-- Service       : stay-service
-- Schema owner  : JPA entity Stay
-- Soft-delete   : active column + @SQLDelete / @SQLRestriction
-- Auditing      : created_at / updated_at via Spring Data @CreatedDate
-- UUID strategy : gen_random_uuid() (pg >= 13, no extension needed)
-- Enums         : StayStatus → ACTIVE, CHECKED_OUT, CANCELLED
-- Cross-service : reservation_id, guest_id, room_id are logical FK refs
--                 (no DB FK – services own their own schemas in microservices)
-- ============================================================

-- ---------------------------------------------------------------
-- stays
-- Maps to: @Entity @Table(name = "stays") Stay.java
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stays (
    id                    UUID        NOT NULL DEFAULT gen_random_uuid(),
    hotel_id              UUID,
    reservation_id        UUID        NOT NULL,
    guest_id              UUID        NOT NULL,
    room_id               UUID        NOT NULL,
    status                VARCHAR(20) NOT NULL,
    actual_check_in_time  TIMESTAMP,
    actual_check_out_time TIMESTAMP,
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP   NOT NULL,
    -- updated_at has insertable=false in the entity, so it may be NULL on first insert
    updated_at            TIMESTAMP,

    CONSTRAINT pk_stays               PRIMARY KEY (id),
    CONSTRAINT chk_stays_status       CHECK (status IN (
        'ACTIVE', 'CHECKED_OUT', 'CANCELLED'
    )),
    CONSTRAINT chk_stays_checkout_after_checkin CHECK (
        actual_check_out_time IS NULL
        OR actual_check_in_time IS NULL
        OR actual_check_out_time > actual_check_in_time
    )
);

COMMENT ON TABLE  stays                        IS 'In-house stays that link a confirmed guest, reservation, and room into an operational record.';
COMMENT ON COLUMN stays.id                     IS 'Surrogate UUID primary key, generated server-side.';
COMMENT ON COLUMN stays.hotel_id               IS 'Optional hotel identifier for multi-property deployments.';
COMMENT ON COLUMN stays.reservation_id         IS 'Logical reference to a reservation in reservation-service (cross-service, no DB FK).';
COMMENT ON COLUMN stays.guest_id               IS 'Logical reference to a guest in guest-service (cross-service, no DB FK).';
COMMENT ON COLUMN stays.room_id                IS 'Logical reference to a room in inventory-service (cross-service, no DB FK).';
COMMENT ON COLUMN stays.status                 IS 'Lifecycle stage: ACTIVE | CHECKED_OUT | CANCELLED.';
COMMENT ON COLUMN stays.actual_check_in_time   IS 'Timestamp when the guest physically checked in at reception.';
COMMENT ON COLUMN stays.actual_check_out_time  IS 'Timestamp when the guest physically checked out. NULL while in-house.';
COMMENT ON COLUMN stays.active                 IS 'Soft-delete flag. FALSE = logically deleted; filtered by @SQLRestriction.';
COMMENT ON COLUMN stays.created_at             IS 'Record creation timestamp, managed by Spring Data Auditing.';
COMMENT ON COLUMN stays.updated_at             IS 'Last modification timestamp, managed by Spring Data Auditing (insertable=false).';

-- Partial index mirroring @SQLRestriction("active = true")
CREATE INDEX IF NOT EXISTS idx_stays_active
    ON stays (active)
    WHERE active = TRUE;

-- Index for status-based operational queries (front-desk dashboard)
CREATE INDEX IF NOT EXISTS idx_stays_status
    ON stays (status)
    WHERE active = TRUE;

-- Index for cross-service lookup: find stay by reservation
CREATE INDEX IF NOT EXISTS idx_stays_reservation_id
    ON stays (reservation_id)
    WHERE active = TRUE;

-- Index for cross-service lookup: find stays per guest (history)
CREATE INDEX IF NOT EXISTS idx_stays_guest_id
    ON stays (guest_id)
    WHERE active = TRUE;

-- Index for cross-service lookup: find current stay for a room
CREATE INDEX IF NOT EXISTS idx_stays_room_id
    ON stays (room_id)
    WHERE active = TRUE;

-- Index for multi-tenancy queries
CREATE INDEX IF NOT EXISTS idx_stays_hotel_id
    ON stays (hotel_id)
    WHERE active = TRUE;
