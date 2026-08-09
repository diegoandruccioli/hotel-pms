-- ============================================================
-- Flyway migration: V10__add_quotations.sql
-- Service  : frontdesk-service
-- Purpose  : Quotations (preventivi) — a priced offer for a stay, sent to a
--            guest or a not-yet-persisted prospect, convertible to a real
--            Reservation. Deliberately NOT a Reservation/PENDING row: a
--            quotation must never block room availability or count as an
--            "active reservation" for the guest-service GDPR legal-hold
--            guard (see RatePricingService/ReservationService javadoc for
--            the reconciliation precedent this follows).
-- ============================================================

CREATE TABLE IF NOT EXISTS quotations (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid(),
    hotel_id             UUID           NOT NULL,
    guest_id             UUID,
    prospect_first_name  VARCHAR(100),
    prospect_last_name   VARCHAR(100),
    prospect_email       VARCHAR(255),
    check_in_date        DATE           NOT NULL,
    check_out_date       DATE           NOT NULL,
    expected_guests      INT,
    status               VARCHAR(20)    NOT NULL,
    valid_until          DATE           NOT NULL,
    total_price          NUMERIC(10, 2) NOT NULL,
    send_failed          BOOLEAN        NOT NULL DEFAULT FALSE,
    send_failure_reason  VARCHAR(500),
    active               BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP      NOT NULL,
    updated_at           TIMESTAMP      NOT NULL,

    CONSTRAINT pk_quotations        PRIMARY KEY (id),
    CONSTRAINT chk_quotations_dates CHECK (check_out_date > check_in_date),
    CONSTRAINT chk_quotations_status CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'DECLINED')),
    -- Either a persisted guest, or enough of a prospect to email a quote to —
    -- never neither (mirrors GuestRequest's email-or-phone @AssertTrue at the
    -- API boundary; this is the DB-level backstop).
    CONSTRAINT chk_quotations_guest_or_prospect CHECK (
        guest_id IS NOT NULL
        OR (prospect_first_name IS NOT NULL AND prospect_last_name IS NOT NULL AND prospect_email IS NOT NULL)
    )
);

COMMENT ON TABLE quotations IS 'Priced stay offers, convertible to a Reservation. Not a Reservation row itself — never blocks availability or counts toward GDPR legal-hold.';
COMMENT ON COLUMN quotations.status IS 'DRAFT/SENT/ACCEPTED/DECLINED — EXPIRED is computed at read time from valid_until, never persisted.';
COMMENT ON COLUMN quotations.total_price IS 'Sum of quotation_line_items.price, resolved server-side via RatePricingService at creation time.';

CREATE TABLE IF NOT EXISTS quotation_line_items (
    id            UUID           NOT NULL DEFAULT gen_random_uuid(),
    quotation_id  UUID           NOT NULL,
    room_id       UUID           NOT NULL,
    price         NUMERIC(10, 2) NOT NULL,
    active        BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP      NOT NULL,
    updated_at    TIMESTAMP      NOT NULL,

    CONSTRAINT pk_quotation_line_items         PRIMARY KEY (id),
    CONSTRAINT fk_qli_quotation                FOREIGN KEY (quotation_id)
                                                    REFERENCES quotations (id)
                                                    ON DELETE CASCADE,
    CONSTRAINT fk_qli_room                     FOREIGN KEY (room_id)
                                                    REFERENCES rooms (id)
                                                    ON DELETE RESTRICT,
    CONSTRAINT chk_qli_price                   CHECK (price >= 0)
);

CREATE INDEX IF NOT EXISTS idx_quotations_hotel_id
    ON quotations (hotel_id)
    WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_quotation_line_items_quotation_id
    ON quotation_line_items (quotation_id)
    WHERE active = TRUE;
