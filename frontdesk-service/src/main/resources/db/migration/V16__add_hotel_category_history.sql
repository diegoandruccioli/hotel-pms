-- E18: per-hotel classification (stelle/categoria) history.
--
-- Several comuni (Cattolica included) tier the tourist-tax rate by the
-- structure's category/star rating, not a flat per-comune amount. A hotel's
-- category can change over time (e.g. a star-rating upgrade) — a stay must
-- always be assessed against the category the hotel actually held on its
-- check-in date, never the hotel's current category, so this is append-only
-- and versioned by date range, same shape as rate_seasons (V9) rather than a
-- single mutable field on hotel_settings.
--
-- category is a free-form VARCHAR, not a fixed enum: classification schemes
-- are not standardized nationally (star ratings for hotels, but comuni also
-- tax B&B/case vacanza/campeggi under different category labels) — the exact
-- values in use are whatever a given comune's regolamento names, entered by
-- the operator, not hardcoded here.

CREATE TABLE IF NOT EXISTS hotel_category_history (
    id          UUID      NOT NULL DEFAULT gen_random_uuid(),
    hotel_id    UUID      NOT NULL,
    category    VARCHAR(20) NOT NULL,
    valid_from  DATE      NOT NULL,
    valid_to    DATE,
    created_at  TIMESTAMP NOT NULL,

    CONSTRAINT pk_hotel_category_history PRIMARY KEY (id),
    CONSTRAINT chk_hotel_category_dates  CHECK (valid_to IS NULL OR valid_to > valid_from),

    -- Half-open range ('[)'): closing the current row's valid_to at the same
    -- date the next row's valid_from starts is a clean handoff, not an overlap.
    CONSTRAINT excl_hotel_category_no_overlap
        EXCLUDE USING gist (
            hotel_id WITH =,
            daterange(valid_from, valid_to, '[)') WITH &&
        )
);

COMMENT ON TABLE  hotel_category_history            IS 'Append-only history of a hotel''s classification/category (stelle), used to resolve the correct tourist-tax rate tier for a stay by its check-in date.';
COMMENT ON COLUMN hotel_category_history.category   IS 'Free-form classification label (e.g. comune-specific stelle/category naming) — not a fixed enum, entered by the operator.';
COMMENT ON COLUMN hotel_category_history.valid_to   IS 'NULL = this is the hotel''s current category. Never mutated in place to "correct" a past entry; close it and insert a new row instead.';

CREATE INDEX IF NOT EXISTS idx_hotel_category_history_hotel_id
    ON hotel_category_history (hotel_id);
