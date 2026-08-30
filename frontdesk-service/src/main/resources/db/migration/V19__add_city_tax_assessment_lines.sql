-- E18 follow-up (Parte 6): per-night-segment breakdown for a tourist-tax
-- assessment. Before this migration, CityTaxCalculator resolved a single rate
-- at the stay's first night and applied it to every night — but comune
-- delibere almost always tax the night actually stayed at the rate in effect
-- that night, and delibere typically change 1 January or 1 April, exactly
-- when stays commonly cross the boundary (Capodanno, Pasqua). A stay running
-- 29 Dec - 3 Jan was taxed entirely at the old rate; that was a calculation
-- error, not a design choice — city_tax_assessments.total_amount_snapshot
-- being immutable once assessed (fiscal audit requirement) was always
-- correct and stays correct here, only the resolution that feeds it changes.
--
-- city_tax_assessments keeps amount_per_night_snapshot etc as a denormalized
-- first-segment reference (display, the CITY_TAX charge description) and
-- total_amount as the sum of every line's subtotal; these lines are the
-- fiscally authoritative breakdown — independently verifiable in an audit,
-- and the exact grain (presenze per periodo) a comune declaration needs.
--
-- Immutable once written, same as city_tax_assessments itself: a
-- rectification (guest added, stay extended) adds new lines for the
-- additional period, never edits an existing one.

CREATE TABLE IF NOT EXISTS city_tax_assessment_lines (
    id                UUID           NOT NULL DEFAULT gen_random_uuid(),
    assessment_id     UUID           NOT NULL,
    from_date         DATE           NOT NULL,
    to_date           DATE           NOT NULL,
    city_tax_rate_id  UUID           NOT NULL,
    amount_per_night  NUMERIC(10, 2) NOT NULL,
    taxable_guests    INTEGER        NOT NULL,
    taxable_nights    INTEGER        NOT NULL,
    subtotal          NUMERIC(10, 2) NOT NULL,
    created_at        TIMESTAMP      NOT NULL,

    CONSTRAINT pk_city_tax_assessment_lines         PRIMARY KEY (id),
    CONSTRAINT fk_city_tax_assessment_lines_assessment
        FOREIGN KEY (assessment_id) REFERENCES city_tax_assessments (id),
    CONSTRAINT fk_city_tax_assessment_lines_rate
        FOREIGN KEY (city_tax_rate_id) REFERENCES city_tax_rates (id),
    CONSTRAINT chk_city_tax_assessment_lines_dates  CHECK (to_date > from_date),
    CONSTRAINT chk_city_tax_assessment_lines_nights CHECK (taxable_nights >= 0),
    CONSTRAINT chk_city_tax_assessment_lines_guests CHECK (taxable_guests >= 0),
    CONSTRAINT chk_city_tax_assessment_lines_amount CHECK (subtotal >= 0)
);

COMMENT ON TABLE  city_tax_assessment_lines                 IS 'One homogeneous-rate segment of a tourist-tax assessment, one row per rate actually in effect during the stay. Immutable — a rectification adds lines, never edits one.';
COMMENT ON COLUMN city_tax_assessment_lines.to_date          IS 'Exclusive — the night before is the last taxed night in this segment.';
COMMENT ON COLUMN city_tax_assessment_lines.taxable_nights   IS 'Nights actually taxed in this segment, after this rate''s own maxTaxableNights cap — can be less than (to_date - from_date) when capped.';

CREATE INDEX IF NOT EXISTS idx_city_tax_assessment_lines_assessment_id
    ON city_tax_assessment_lines (assessment_id);
