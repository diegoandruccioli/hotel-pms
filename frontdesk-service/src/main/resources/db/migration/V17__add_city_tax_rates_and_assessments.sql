-- E18: tourist-tax rate rules and per-stay assessment audit trail.
--
-- city_tax_rates: append-only, per (hotel, comune, category) — deliberately
-- NOT shared across hotels in the same comune, even though a comune's
-- regolamento sets the same rate for every same-category structure in it.
-- Sharing this table cross-tenant would need a new authorization model this
-- codebase has nowhere else (every other writable table is hotel-owned,
-- enforced by TenantIsolationArchTest since T-BILL-04/T-STAY-06) — a small
-- amount of duplicate data entry across hotels in the same comune is a far
-- smaller risk than a cross-tenant write hole on fiscal configuration.
--
-- No soft-delete, no @SQLDelete, no @Version: a fiscal rate rule is never
-- mutated or logically removed once city_tax_assessments can reference it —
-- "editing" a tariff means closing the current row (valid_to) and inserting a
-- new one. The EXCLUDE constraint is the concurrency guard, same role it
-- plays for rate_seasons (V9) and hotel_category_history (V16).

CREATE TABLE IF NOT EXISTS city_tax_rates (
    id                 UUID           NOT NULL DEFAULT gen_random_uuid(),
    hotel_id           UUID           NOT NULL,
    comune_codice      VARCHAR(9)     NOT NULL,
    category           VARCHAR(20)    NOT NULL,
    amount_per_night   NUMERIC(10, 2) NOT NULL,
    max_taxable_nights INTEGER,
    exempt_under_age   INTEGER,
    valid_from         DATE           NOT NULL,
    valid_to           DATE,
    note               VARCHAR(200),
    created_at         TIMESTAMP      NOT NULL,
    updated_at         TIMESTAMP      NOT NULL,

    CONSTRAINT pk_city_tax_rates              PRIMARY KEY (id),
    CONSTRAINT fk_city_tax_rates_comune       FOREIGN KEY (comune_codice)
                                                   REFERENCES alloggiati_comuni (codice),
    CONSTRAINT chk_city_tax_rates_amount      CHECK (amount_per_night >= 0),
    CONSTRAINT chk_city_tax_rates_max_nights  CHECK (max_taxable_nights IS NULL OR max_taxable_nights > 0),
    CONSTRAINT chk_city_tax_rates_exempt_age  CHECK (exempt_under_age IS NULL OR exempt_under_age BETWEEN 0 AND 120),
    CONSTRAINT chk_city_tax_rates_dates       CHECK (valid_to IS NULL OR valid_to > valid_from),

    -- Half-open range: a tariff change is effective *on* valid_from, so the
    -- previous rule's valid_to equalling the new rule's valid_from is a clean
    -- handoff, not an overlap. See rate_seasons (V9) for the closed-range
    -- equivalent used where boundary semantics differ.
    CONSTRAINT excl_city_tax_rates_no_overlap
        EXCLUDE USING gist (
            hotel_id WITH =,
            comune_codice WITH =,
            category WITH =,
            daterange(valid_from, valid_to, '[)') WITH &&
        )
);

COMMENT ON TABLE  city_tax_rates                    IS 'Versioned per-hotel tourist-tax rate rules (art. 4 D.Lgs. 23/2011). Append-only — a tariff change closes the current row and adds a new one.';
COMMENT ON COLUMN city_tax_rates.max_taxable_nights IS 'Cap on the number of nights the tax applies to; NULL = uncapped.';
COMMENT ON COLUMN city_tax_rates.exempt_under_age   IS 'Guests strictly under this age at check-in are exempt; NULL = no age exemption.';
COMMENT ON COLUMN city_tax_rates.valid_from         IS 'Per art. 52 D.Lgs. 446/1997, a comune tariff deliberation is effective no earlier than the first day of the second month following its publication. Not machine-enforced — the operator enters the date from the delibera.';
COMMENT ON COLUMN city_tax_rates.note                IS 'Free-text reference to the delibera/regolamento this rule comes from, for audit purposes.';

CREATE INDEX IF NOT EXISTS idx_city_tax_rates_hotel_id ON city_tax_rates (hotel_id);

-- city_tax_assessments: immutable per-stay audit record. The FK gives
-- provenance (which delibera applied); the snapshot columns make the amount
-- reconstructable even if the rule row it pointed to were ever gone —
-- required for fiscal audit ("what was actually charged, when, under what
-- rule"), same reasoning as invoice_fiscal_exports (billing-service) for
-- FatturaPA XML snapshots.
CREATE TABLE IF NOT EXISTS city_tax_assessments (
    id                            UUID           NOT NULL DEFAULT gen_random_uuid(),
    hotel_id                      UUID           NOT NULL,
    stay_id                       UUID           NOT NULL,
    city_tax_rate_id              UUID,
    amount_per_night_snapshot     NUMERIC(10, 2) NOT NULL,
    max_taxable_nights_snapshot   INTEGER,
    exempt_under_age_snapshot     INTEGER,
    taxable_guests                INTEGER        NOT NULL,
    taxable_nights                INTEGER        NOT NULL,
    total_amount                  NUMERIC(10, 2) NOT NULL,
    billing_charge_id             UUID,
    assessed_at                   TIMESTAMP      NOT NULL,

    CONSTRAINT pk_city_tax_assessments      PRIMARY KEY (id),
    CONSTRAINT uq_city_tax_assessments_stay UNIQUE (stay_id),
    CONSTRAINT fk_city_tax_assessments_rate FOREIGN KEY (city_tax_rate_id)
                                                 REFERENCES city_tax_rates (id),
    CONSTRAINT chk_city_tax_assessments_amount CHECK (total_amount >= 0)
);

COMMENT ON TABLE  city_tax_assessments                    IS 'Immutable per-stay tourist-tax calculation, one row per stay. Never recomputed after the fact — the amount actually charged must survive a later rate change unchanged.';
COMMENT ON COLUMN city_tax_assessments.city_tax_rate_id   IS 'The rule applied, if any was configured at check-in time. NULL when comune/category/rate were not configured — the assessment row still records that "zero was assessed because unconfigured", distinct from "assessed and zero because all guests exempt".';
COMMENT ON COLUMN city_tax_assessments.billing_charge_id  IS 'The CITY_TAX charge id returned by billing-service, once posted. Doubles as the per-charge idempotency guard for the check-in retry path — NULL means "not yet posted".';

CREATE INDEX IF NOT EXISTS idx_city_tax_assessments_hotel_id ON city_tax_assessments (hotel_id);

-- Per-charge idempotency for StayBillingCoordinator: today a single boolean
-- (invoiceCreationFailed) guards retry of the one charge it posts (ROOM_NIGHT).
-- Adding CITY_TAX as a second charge on the same retry path means a
-- CITY_TAX-only failure could otherwise re-post ROOM_NIGHT on retry —
-- room_charge_id makes that guard per-charge instead of per-invoice.
ALTER TABLE stays
    ADD COLUMN room_charge_id UUID;

COMMENT ON COLUMN stays.room_charge_id IS 'The ROOM_NIGHT charge id returned by billing-service, once posted. Per-charge idempotency guard for the check-in retry path, paired with city_tax_assessments.billing_charge_id for CITY_TAX.';
