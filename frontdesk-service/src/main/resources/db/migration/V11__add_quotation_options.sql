-- ============================================================
-- Flyway migration: V11__add_quotation_options.sql
-- Service  : frontdesk-service
-- Purpose  : Multi-option quotations — a quotation may offer 2-5 alternative
--            room combinations (e.g. "Standard Double" vs "Suite") for the
--            guest to choose from, instead of a single fixed offer.
--            Additive only: quotation_line_items.quotation_id (FK to
--            quotations) is kept as-is, even though it becomes redundant
--            with the new quotation_option_id — no column is dropped or
--            renamed, so nothing that already queries quotation_line_items
--            by quotation_id breaks.
-- ============================================================

CREATE TABLE IF NOT EXISTS quotation_options (
    id           UUID           NOT NULL DEFAULT gen_random_uuid(),
    quotation_id UUID           NOT NULL,
    label        VARCHAR(100)   NOT NULL,
    position     INT            NOT NULL,
    total_price  NUMERIC(10, 2) NOT NULL,
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP      NOT NULL,
    updated_at   TIMESTAMP      NOT NULL,

    CONSTRAINT pk_quotation_options PRIMARY KEY (id),
    CONSTRAINT fk_qo_quotation      FOREIGN KEY (quotation_id)
                                         REFERENCES quotations (id)
                                         ON DELETE CASCADE,
    CONSTRAINT chk_qo_total_price   CHECK (total_price >= 0)
);

COMMENT ON TABLE quotation_options IS 'One alternative room combination within a quotation (1-5 per quotation). Every quotation has at least one.';
COMMENT ON COLUMN quotation_options.position IS 'Display order, 0-based, as chosen at creation time.';

CREATE INDEX IF NOT EXISTS idx_quotation_options_quotation_id
    ON quotation_options (quotation_id)
    WHERE active = TRUE;

-- --------------------------------------------------------------
-- Backfill: every existing quotation gets exactly one option
-- carrying its current line items and total_price, before the
-- new column is made NOT NULL.
-- --------------------------------------------------------------

ALTER TABLE quotation_line_items ADD COLUMN quotation_option_id UUID;

INSERT INTO quotation_options (id, quotation_id, label, position, total_price, active, created_at, updated_at)
SELECT gen_random_uuid(), q.id, 'Opzione 1', 0, q.total_price, TRUE, now(), now()
FROM quotations q;

UPDATE quotation_line_items li
SET quotation_option_id = qo.id
FROM quotation_options qo
WHERE qo.quotation_id = li.quotation_id;

ALTER TABLE quotation_line_items
    ALTER COLUMN quotation_option_id SET NOT NULL;

ALTER TABLE quotation_line_items
    ADD CONSTRAINT fk_qli_quotation_option FOREIGN KEY (quotation_option_id)
        REFERENCES quotation_options (id)
        ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_quotation_line_items_option_id
    ON quotation_line_items (quotation_option_id)
    WHERE active = TRUE;

-- --------------------------------------------------------------
-- Which option the guest accepted at conversion time. Nullable:
-- unset until POST /{id}/convert picks one (or the quotation is
-- never converted at all).
-- --------------------------------------------------------------

ALTER TABLE quotations ADD COLUMN accepted_option_id UUID;

ALTER TABLE quotations
    ADD CONSTRAINT fk_quotations_accepted_option FOREIGN KEY (accepted_option_id)
        REFERENCES quotation_options (id);

COMMENT ON COLUMN quotations.total_price IS 'Lowest total_price across this quotation''s options — used for list sorting/display. Individual option totals live on quotation_options.total_price.';
