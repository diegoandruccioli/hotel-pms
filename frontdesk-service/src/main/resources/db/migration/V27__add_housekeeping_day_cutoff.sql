-- Housekeeping worksheet: the "which date is tonight's shift printing for" problem.
-- A bare LocalDate.now() flips at midnight JVM-local, which is wrong for a hotel's
-- night shift (e.g. 02:00 should still resolve to yesterday's business date). These
-- two columns let BusinessDateResolverImpl compute that per hotel instead of guessing.
ALTER TABLE hotel_settings
    ADD COLUMN timezone                       VARCHAR(64) NOT NULL DEFAULT 'Europe/Rome',
    ADD COLUMN housekeeping_day_cutoff_hour    SMALLINT    NOT NULL DEFAULT 4,
    ADD CONSTRAINT chk_housekeeping_cutoff_hour
        CHECK (housekeeping_day_cutoff_hour BETWEEN 0 AND 12);
