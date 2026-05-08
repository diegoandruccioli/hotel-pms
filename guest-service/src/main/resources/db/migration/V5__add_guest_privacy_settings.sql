-- T-GST-05: per-hotel GDPR retention settings.
-- guest_retention_years: minimum period (years) after last stay before
-- anonymisation. Must not be set below the TULPS legal minimum (5 years).
-- Application-layer validation prevents values below MIN_RETENTION_YEARS.
CREATE TABLE guest_privacy_settings (
    hotel_id              UUID        PRIMARY KEY,
    guest_retention_years INT         NOT NULL DEFAULT 5,
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP
);
