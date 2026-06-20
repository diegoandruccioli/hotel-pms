-- ============================================================
-- Flyway migration: V1__frontdesk_baseline.sql
-- Service       : frontdesk-service
-- Consolidates  : inventory-service (V1-V3) + reservation-service (V1-V4)
--                 + stay-service (V1-V9) — see ADR-001 in backup/DECISIONS.md.
-- Rebaselined rather than concatenated: no production data to preserve
-- (pre-production/pilot stage). The original per-service migration
-- history remains on git history / the pre-consolidation branches.
--
-- Payoff of the consolidation: reservation_line_items.room_id,
-- stays.room_id and stays.reservation_id were previously "logical FKs"
-- across 3 separate databases (comments only, no DB-level integrity).
-- Now that rooms/reservations/stays live in one database, real FOREIGN
-- KEY constraints are added — see the "NEW" comments below.
--
-- Still cross-service (no FK, by design): guest_id (guest-service),
-- invoice_id (billing-service) — those remain separate microservices.
-- ============================================================

-- ---------------------------------------------------------------
-- room_types
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
COMMENT ON COLUMN room_types.active       IS 'Soft-delete flag. False = logically deleted; filtered by @SQLRestriction.';

CREATE INDEX IF NOT EXISTS idx_room_types_active
    ON room_types (active)
    WHERE active = TRUE;

-- ---------------------------------------------------------------
-- rooms
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rooms (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    hotel_id     UUID         NOT NULL,
    room_number  VARCHAR(50)  NOT NULL,
    room_type_id UUID         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,

    CONSTRAINT pk_rooms             PRIMARY KEY (id),
    CONSTRAINT uq_rooms_room_number UNIQUE (room_number),
    CONSTRAINT fk_rooms_room_type   FOREIGN KEY (room_type_id)
                                        REFERENCES room_types (id)
                                        ON UPDATE CASCADE
                                        ON DELETE RESTRICT,
    CONSTRAINT chk_rooms_status     CHECK (status IN ('CLEAN', 'DIRTY', 'MAINTENANCE', 'OCCUPIED'))
);

COMMENT ON TABLE  rooms              IS 'Physical hotel rooms, each linked to a room type.';
COMMENT ON COLUMN rooms.hotel_id     IS 'Hotel identifier for multi-tenant isolation. NOT NULL.';
COMMENT ON COLUMN rooms.status       IS 'Operational status: CLEAN | DIRTY | MAINTENANCE | OCCUPIED (set by the check-in Saga).';
COMMENT ON COLUMN rooms.active       IS 'Soft-delete flag. False = logically deleted; filtered by @SQLRestriction.';

CREATE INDEX IF NOT EXISTS idx_rooms_active       ON rooms (active)       WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_rooms_room_type_id ON rooms (room_type_id);
CREATE INDEX IF NOT EXISTS idx_rooms_status       ON rooms (status)       WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_rooms_hotel_id     ON rooms (hotel_id)     WHERE active = TRUE;

-- ---------------------------------------------------------------
-- reservations
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reservations (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    version         BIGINT         NOT NULL DEFAULT 0,
    hotel_id        UUID,
    guest_id        UUID           NOT NULL,
    expected_guests INT            NOT NULL,
    actual_guests   INT            NOT NULL DEFAULT 0,
    check_in_date   DATE           NOT NULL,
    check_out_date  DATE           NOT NULL,
    status          VARCHAR(50)    NOT NULL,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP      NOT NULL,
    updated_at      TIMESTAMP      NOT NULL,

    CONSTRAINT pk_reservations         PRIMARY KEY (id),
    CONSTRAINT chk_reservations_dates  CHECK (check_out_date > check_in_date),
    CONSTRAINT chk_reservations_status CHECK (status IN (
        'PENDING', 'CONFIRMED', 'PARTIALLY_CHECKED_IN', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED', 'NO_SHOW'
    ))
);

COMMENT ON TABLE  reservations            IS 'Booking records linking a guest to one or more rooms over a date range.';
COMMENT ON COLUMN reservations.guest_id   IS 'UUID reference to the primary guest in guest-service (cross-service, no FK).';
COMMENT ON COLUMN reservations.version    IS 'JPA optimistic-lock counter; stale-version conflict returns HTTP 409.';
COMMENT ON COLUMN reservations.active     IS 'Soft-delete flag. FALSE = logically deleted; filtered by @SQLRestriction.';

CREATE INDEX IF NOT EXISTS idx_reservations_active         ON reservations (active)         WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_reservations_check_in_date  ON reservations (check_in_date)  WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_reservations_check_out_date ON reservations (check_out_date) WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_reservations_hotel_id       ON reservations (hotel_id)       WHERE active = TRUE;

-- ---------------------------------------------------------------
-- reservation_line_items
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reservation_line_items (
    id             UUID           NOT NULL DEFAULT gen_random_uuid(),
    reservation_id UUID           NOT NULL,
    room_id        UUID           NOT NULL,
    price          NUMERIC(10, 2) NOT NULL,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP      NOT NULL,
    updated_at     TIMESTAMP      NOT NULL,

    CONSTRAINT pk_reservation_line_items PRIMARY KEY (id),
    CONSTRAINT fk_rli_reservation        FOREIGN KEY (reservation_id)
                                             REFERENCES reservations (id)
                                             ON UPDATE CASCADE
                                             ON DELETE CASCADE,
    -- NEW (ADR-001 payoff): room_id was a logical-only cross-service ref to
    -- inventory-service before the consolidation. Real FK now possible.
    CONSTRAINT fk_rli_room                FOREIGN KEY (room_id)
                                             REFERENCES rooms (id)
                                             ON UPDATE CASCADE
                                             ON DELETE RESTRICT,
    CONSTRAINT chk_rli_price              CHECK (price >= 0)
);

COMMENT ON TABLE reservation_line_items IS 'Individual room assignments within a reservation.';

CREATE INDEX IF NOT EXISTS idx_rli_active         ON reservation_line_items (active) WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_rli_reservation_id ON reservation_line_items (reservation_id);
CREATE INDEX IF NOT EXISTS idx_rli_room_id        ON reservation_line_items (room_id) WHERE active = TRUE;

-- ---------------------------------------------------------------
-- stays
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stays (
    id                       UUID        NOT NULL DEFAULT gen_random_uuid(),
    hotel_id                 UUID,
    reservation_id           UUID        NOT NULL,
    guest_id                 UUID        NOT NULL,
    room_id                  UUID        NOT NULL,
    status                   VARCHAR(20) NOT NULL,
    actual_check_in_time     TIMESTAMP,
    actual_check_out_time    TIMESTAMP,
    expected_check_out_date  DATE,
    invoice_id               UUID,
    alloggiati_sent          BOOLEAN     NOT NULL DEFAULT FALSE,
    guest_display_name       VARCHAR(255),
    room_number              VARCHAR(50),
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMP   NOT NULL,
    updated_at                TIMESTAMP,

    CONSTRAINT pk_stays         PRIMARY KEY (id),
    CONSTRAINT chk_stays_status CHECK (status IN ('EXPECTED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED')),
    CONSTRAINT chk_stays_checkout_after_checkin CHECK (
        actual_check_out_time IS NULL
        OR actual_check_in_time IS NULL
        OR actual_check_out_time > actual_check_in_time
    ),
    -- NEW (ADR-001 payoff): reservation_id/room_id were logical-only
    -- cross-service refs before the consolidation. Real FKs now possible.
    CONSTRAINT fk_stays_reservation FOREIGN KEY (reservation_id)
                                        REFERENCES reservations (id)
                                        ON UPDATE CASCADE
                                        ON DELETE RESTRICT,
    CONSTRAINT fk_stays_room        FOREIGN KEY (room_id)
                                        REFERENCES rooms (id)
                                        ON UPDATE CASCADE
                                        ON DELETE RESTRICT
);

COMMENT ON TABLE  stays                       IS 'In-house stays that link a confirmed guest, reservation, and room into an operational record.';
COMMENT ON COLUMN stays.guest_id              IS 'Logical reference to a guest in guest-service (cross-service, no DB FK).';
COMMENT ON COLUMN stays.invoice_id            IS 'Logical reference to the billing invoice opened at check-in (cross-service, no DB FK).';
COMMENT ON COLUMN stays.guest_display_name    IS 'Denormalized "Cognome Nome" of the primary guest, captured at check-in.';
COMMENT ON COLUMN stays.room_number           IS 'Denormalized room number, captured at check-in.';
COMMENT ON COLUMN stays.active                IS 'Soft-delete flag. FALSE = logically deleted; filtered by @SQLRestriction.';

CREATE INDEX IF NOT EXISTS idx_stays_active         ON stays (active)         WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_stays_status         ON stays (status)         WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_stays_reservation_id ON stays (reservation_id) WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_stays_guest_id       ON stays (guest_id)       WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_stays_room_id        ON stays (room_id)        WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_stays_hotel_id       ON stays (hotel_id)       WHERE active = TRUE;

-- ---------------------------------------------------------------
-- stay_guests
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stay_guests (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    stay_id                 UUID         NOT NULL,
    first_name              VARCHAR(100) NOT NULL,
    last_name               VARCHAR(100) NOT NULL,
    gender                  VARCHAR(20)  NOT NULL,
    date_of_birth           DATE         NOT NULL,
    place_of_birth          VARCHAR(100) NOT NULL,
    citizenship             VARCHAR(100) NOT NULL,
    -- Nullable: FAMILIARE/MEMBRO_GRUPPO (TIPALLOG 19/20) carry no document per tracciato rules.
    document_type           VARCHAR(50),
    document_number         VARCHAR(100),
    document_place_of_issue VARCHAR(100),
    is_primary_guest        BOOLEAN      NOT NULL DEFAULT FALSE,
    traveller_type          VARCHAR(20),
    travel_purpose          VARCHAR(100),
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,

    CONSTRAINT pk_stay_guests      PRIMARY KEY (id),
    CONSTRAINT fk_stay_guests_stay FOREIGN KEY (stay_id) REFERENCES stays (id)
                                       ON UPDATE CASCADE
                                       ON DELETE CASCADE
);

COMMENT ON TABLE  stay_guests         IS 'Detailed accompanying guest data for Alloggiati Web compliance.';
COMMENT ON COLUMN stay_guests.stay_id IS 'FK -> stays.id, the parent stay record.';

CREATE INDEX IF NOT EXISTS idx_stay_guests_stay_id ON stay_guests (stay_id) WHERE active = TRUE;

-- ---------------------------------------------------------------
-- hotel_settings
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hotel_settings (
    hotel_id             UUID         NOT NULL,
    alloggiati_auto_send BOOLEAN      NOT NULL DEFAULT FALSE,
    hotel_name           VARCHAR(150),
    address              VARCHAR(200),
    vat_number           VARCHAR(20),
    fiscal_code          VARCHAR(20),
    logo_url             VARCHAR(500),
    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP,

    CONSTRAINT pk_hotel_settings PRIMARY KEY (hotel_id)
);

COMMENT ON TABLE hotel_settings IS 'Per-hotel operational settings (Alloggiati auto-send, profile fields for invoices).';

-- ---------------------------------------------------------------
-- Alloggiati Web lookup tables (Portale Alloggiati Web)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alloggiati_stati (
    codice        VARCHAR(9)   NOT NULL,
    descrizione   VARCHAR(100) NOT NULL,
    data_fine_val DATE,
    CONSTRAINT pk_alloggiati_stati PRIMARY KEY (codice)
);

CREATE TABLE IF NOT EXISTS alloggiati_comuni (
    codice        VARCHAR(9)   NOT NULL,
    descrizione   VARCHAR(100) NOT NULL,
    provincia     VARCHAR(2)   NOT NULL,
    data_fine_val DATE,
    CONSTRAINT pk_alloggiati_comuni PRIMARY KEY (codice)
);
CREATE INDEX IF NOT EXISTS idx_alloggiati_comuni_provincia   ON alloggiati_comuni (provincia);
CREATE INDEX IF NOT EXISTS idx_alloggiati_comuni_descrizione ON alloggiati_comuni (LOWER(descrizione) varchar_pattern_ops);

CREATE TABLE IF NOT EXISTS alloggiati_tipdoc (
    codice      VARCHAR(5)   NOT NULL,
    descrizione VARCHAR(100) NOT NULL,
    CONSTRAINT pk_alloggiati_tipdoc PRIMARY KEY (codice)
);
