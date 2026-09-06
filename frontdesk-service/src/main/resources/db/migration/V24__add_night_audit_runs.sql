-- Night audit: closes the operational/financial books for a business date.
-- Immutable once COMPLETED (the service layer rejects re-running an already
-- closed date) — a FAILED row can be retried, since it recorded no real
-- closing. UNIQUE(hotel_id, business_date) is both the idempotency guard and
-- the concurrency guard: two overlapping runs for the same date race on the
-- insert, the loser gets a constraint violation instead of a duplicate close.

CREATE TABLE night_audit_runs (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hotel_id              UUID NOT NULL,
    business_date         DATE NOT NULL,
    status                VARCHAR(20) NOT NULL,
    started_at            TIMESTAMP NOT NULL,
    completed_at          TIMESTAMP,
    run_by                VARCHAR(100) NOT NULL,
    arrivals              INTEGER,
    departures            INTEGER,
    guests_in_house       BIGINT,
    current_stays         BIGINT,
    available_rooms       INTEGER,
    no_shows_marked       INTEGER,
    cash_summary_json     TEXT,
    cash_summary_degraded BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason        VARCHAR(500),
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_night_audit_runs_hotel_date UNIQUE (hotel_id, business_date)
);

CREATE INDEX idx_night_audit_runs_hotel_date ON night_audit_runs (hotel_id, business_date DESC);

COMMENT ON TABLE night_audit_runs IS
    'Immutable per-business-date closing record: auto-detected no-shows, occupancy/arrivals/departures snapshot, cash-by-method summary. One row per (hotel_id, business_date) once COMPLETED.';
COMMENT ON COLUMN night_audit_runs.cash_summary_json IS
    'JSON-serialized list of {paymentMethod, total} from billing-service, captured at run time — never re-derived later, same "amount already reported never silently changes" principle as CityTaxAssessment.';
COMMENT ON COLUMN night_audit_runs.cash_summary_degraded IS
    'True when billing-service was unreachable at run time (circuit-breaker fallback fired) — cash_summary_json is then empty, not a true zero.';
