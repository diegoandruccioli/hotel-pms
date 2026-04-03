-- ============================================================
-- Flyway migration: V1__init_schema.sql
-- Service       : inventory-service
-- Schema owner  : JPA entities Room and RoomType
-- Soft-delete   : active column + @SQLDelete / @SQLRestriction
-- Auditing      : created_at / updated_at via Spring Data @CreatedDate
-- UUID strategy : gen_random_uuid() (pg >= 13, no extension needed)
-- ============================================================

-- Enable the pgcrypto extension once per database if on pg < 13.
-- On pg 13+ gen_random_uuid() is a built-in; this line is a no-op.
-- CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------
-- room_types
-- Maps to: @Entity @Table(name = "room_types") RoomType.java
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS room_types (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    name           VARCHAR(100) NOT NULL,
    description    VARCHAR(500),
    max_occupancy  INTEGER      NOT NULL,
    base_price     NUMERIC(10, 2) NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,

    CONSTRAINT pk_room_types PRIMARY KEY (id),
    CONSTRAINT uq_room_types_name UNIQUE (name),
    CONSTRAINT chk_room_types_max_occupancy CHECK (max_occupancy > 0),
    CONSTRAINT chk_room_types_base_price    CHECK (base_price >= 0)
);

COMMENT ON TABLE  room_types              IS 'Master list of room categories offered by the hotel.';
COMMENT ON COLUMN room_types.id           IS 'Surrogate UUID primary key, generated server-side.';
COMMENT ON COLUMN room_types.name         IS 'Unique, human-readable room type label (e.g. "Deluxe King").';
COMMENT ON COLUMN room_types.description  IS 'Optional marketing description of the room type.';
COMMENT ON COLUMN room_types.max_occupancy IS 'Maximum number of guests allowed in this room type.';
COMMENT ON COLUMN room_types.base_price   IS 'Nightly rack rate used as the pricing baseline.';
COMMENT ON COLUMN room_types.active       IS 'Soft-delete flag. False = logically deleted; filtered by @SQLRestriction.';
COMMENT ON COLUMN room_types.created_at   IS 'Record creation timestamp, managed by Spring Data Auditing.';
COMMENT ON COLUMN room_types.updated_at   IS 'Last modification timestamp, managed by Spring Data Auditing.';

-- Partial indexes only scan active rows – consistent with @SQLRestriction("active = true").
CREATE INDEX IF NOT EXISTS idx_room_types_active
    ON room_types (active)
    WHERE active = TRUE;

-- ---------------------------------------------------------------
-- rooms
-- Maps to: @Entity @Table(name = "rooms") Room.java
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rooms (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    hotel_id     UUID,
    room_number  VARCHAR(50)  NOT NULL,
    room_type_id UUID         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,

    CONSTRAINT pk_rooms                         PRIMARY KEY (id),
    CONSTRAINT uq_rooms_room_number             UNIQUE (room_number),
    CONSTRAINT fk_rooms_room_type               FOREIGN KEY (room_type_id)
                                                    REFERENCES room_types (id)
                                                    ON UPDATE CASCADE
                                                    ON DELETE RESTRICT,
    CONSTRAINT chk_rooms_status                 CHECK (status IN ('CLEAN', 'DIRTY', 'MAINTENANCE'))
);

COMMENT ON TABLE  rooms              IS 'Physical hotel rooms, each linked to a room type.';
COMMENT ON COLUMN rooms.id           IS 'Surrogate UUID primary key, generated server-side.';
COMMENT ON COLUMN rooms.hotel_id     IS 'Optional hotel identifier for multi-property deployments.';
COMMENT ON COLUMN rooms.room_number  IS 'Business identifier (e.g. "101", "PH-1"). Unique across the property.';
COMMENT ON COLUMN rooms.room_type_id IS 'FK → room_types.id; the category this room belongs to.';
COMMENT ON COLUMN rooms.status       IS 'Housekeeping status: CLEAN | DIRTY | MAINTENANCE.';
COMMENT ON COLUMN rooms.active       IS 'Soft-delete flag. False = logically deleted; filtered by @SQLRestriction.';
COMMENT ON COLUMN rooms.created_at   IS 'Record creation timestamp, managed by Spring Data Auditing.';
COMMENT ON COLUMN rooms.updated_at   IS 'Last modification timestamp, managed by Spring Data Auditing.';

-- Partial index for active rooms – mirrors @SQLRestriction("active = true").
CREATE INDEX IF NOT EXISTS idx_rooms_active
    ON rooms (active)
    WHERE active = TRUE;

-- Index on FK to accelerate JOIN queries (room → roomType).
CREATE INDEX IF NOT EXISTS idx_rooms_room_type_id
    ON rooms (room_type_id);

-- Index on housekeeping status for operational dashboard queries.
CREATE INDEX IF NOT EXISTS idx_rooms_status
    ON rooms (status)
    WHERE active = TRUE;
