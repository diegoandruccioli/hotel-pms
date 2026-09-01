-- Bug fix: extendStay (V20/Parte 3) had no optimistic-lock protection at all — two
-- browser tabs extending the same stay's check-out, one forgotten open for a while,
-- would let the second save silently overwrite the first with no conflict warning.
-- Reservation already had this exact class of bug fixed via a version column + a
-- client-echoed version check (see reservations.version); this brings Stay up to the
-- same standard.

ALTER TABLE stays ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN stays.version IS
    'Optimistic-locking version, checked against a client-echoed version on extendStay — same "forgotten tab" protection as reservations.version.';
