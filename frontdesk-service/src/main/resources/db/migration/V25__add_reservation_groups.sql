-- Group management (pilot plan, Punto 4): rooming list, group rate, master folio
-- routing. A reservation stays valid without a group -- this is purely additive.

CREATE TABLE IF NOT EXISTS reservation_groups (
    id                       UUID           NOT NULL DEFAULT gen_random_uuid(),
    version                  BIGINT         NOT NULL DEFAULT 0,
    hotel_id                 UUID           NOT NULL,
    name                     VARCHAR(255)   NOT NULL,
    company_name             VARCHAR(255),
    contact_guest_id         UUID           NOT NULL,
    check_in_date            DATE           NOT NULL,
    check_out_date           DATE           NOT NULL,
    status                   VARCHAR(20)    NOT NULL,
    group_rate_per_night     NUMERIC(10, 2),
    master_folio_invoice_id  UUID,
    notes                    TEXT,
    active                   BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMP      NOT NULL,
    updated_at               TIMESTAMP      NOT NULL,

    CONSTRAINT pk_reservation_groups        PRIMARY KEY (id),
    CONSTRAINT chk_res_groups_dates         CHECK (check_out_date > check_in_date),
    CONSTRAINT chk_res_groups_rate          CHECK (group_rate_per_night IS NULL OR group_rate_per_night >= 0),
    CONSTRAINT chk_res_groups_status        CHECK (status IN (
        'PLANNED', 'CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED'
    ))
);

COMMENT ON TABLE  reservation_groups                      IS 'A named block of rooms booked together (company/agency/party) with an optional group rate and master folio.';
COMMENT ON COLUMN reservation_groups.contact_guest_id     IS 'Logical reference to the group''s contact/responsible guest in guest-service (cross-service, no DB FK) -- also the guestId used for the master folio invoice, if one is opened.';
COMMENT ON COLUMN reservation_groups.group_rate_per_night IS 'When set, takes precedence over the room-type rate calendar for every reservation in this group. NULL means each room prices normally.';
COMMENT ON COLUMN reservation_groups.master_folio_invoice_id IS 'Logical reference to the MASTER invoice in billing-service (cross-service, no DB FK). NULL if the group has no master folio.';
COMMENT ON COLUMN reservation_groups.version              IS 'JPA optimistic-lock counter; stale-version conflict returns HTTP 409.';
COMMENT ON COLUMN reservation_groups.active               IS 'Soft-delete flag. FALSE = logically deleted; filtered by @SQLRestriction.';

CREATE INDEX IF NOT EXISTS idx_reservation_groups_hotel_id ON reservation_groups (hotel_id) WHERE active = TRUE;

ALTER TABLE reservations
    ADD COLUMN group_id               UUID,
    ADD COLUMN billed_to_master_folio BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE reservations
    ADD CONSTRAINT fk_reservations_group FOREIGN KEY (group_id)
                                          REFERENCES reservation_groups (id)
                                          ON UPDATE CASCADE
                                          ON DELETE SET NULL;

COMMENT ON COLUMN reservations.group_id               IS 'The reservation group this room belongs to, if any. A reservation without a group prices/bills exactly as before.';
COMMENT ON COLUMN reservations.billed_to_master_folio IS 'When TRUE and the group has a master folio, this room''s ROOM_NIGHT/CITY_TAX charges are transferred to the group''s master invoice at check-out instead of staying on this room''s individual invoice. Extras (F&B) always stay individual.';

CREATE INDEX IF NOT EXISTS idx_reservations_group_id ON reservations (group_id) WHERE group_id IS NOT NULL;

ALTER TABLE stays
    ADD COLUMN charges_transferred_to_master_folio BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN stays.charges_transferred_to_master_folio IS 'Idempotency guard: TRUE once this stay''s ROOM_NIGHT/CITY_TAX charges have been moved to the group master folio at check-out, so a retried check-out never transfers them twice.';
