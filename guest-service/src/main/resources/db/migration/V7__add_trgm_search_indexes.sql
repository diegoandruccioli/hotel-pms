-- Enable trigram extension for fast LIKE %keyword% searches.
-- pg_trgm allows GIN indexes to accelerate ILIKE and LIKE patterns with leading wildcards,
-- turning a full-table scan (O(n)) into an index scan (O(log n)) for guest search.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- One GIN index per searchable column; partial indexes exclude NULL values to reduce size.
CREATE INDEX IF NOT EXISTS idx_guests_first_name_trgm
    ON guests USING GIN (lower(first_name) gin_trgm_ops)
    WHERE first_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_guests_last_name_trgm
    ON guests USING GIN (lower(last_name) gin_trgm_ops)
    WHERE last_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_guests_email_trgm
    ON guests USING GIN (lower(email) gin_trgm_ops)
    WHERE email IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_guests_city_trgm
    ON guests USING GIN (lower(city) gin_trgm_ops)
    WHERE city IS NOT NULL;
