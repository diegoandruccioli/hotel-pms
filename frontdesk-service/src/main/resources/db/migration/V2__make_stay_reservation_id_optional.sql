-- Walk-in check-ins have no prior reservation by definition (StayServiceImpl
-- explicitly sets reservationId=null for them), but this column was carried
-- over from the ADR-001 consolidation as NOT NULL, making walk-in check-in
-- fail unconditionally with a constraint violation. The FK to reservations
-- already permits NULL values; only this column's own constraint needs to go.
ALTER TABLE stays
    ALTER COLUMN reservation_id DROP NOT NULL;
