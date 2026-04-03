-- Create stay_guests table for Alloggiati Web compliance
CREATE TABLE stay_guests (
    id UUID PRIMARY KEY,
    stay_id UUID NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    gender VARCHAR(20) NOT NULL,
    date_of_birth DATE NOT NULL,
    place_of_birth VARCHAR(100) NOT NULL,
    citizenship VARCHAR(100) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    document_number VARCHAR(100) NOT NULL,
    document_place_of_issue VARCHAR(100) NOT NULL,
    is_primary_guest BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_stay_guests_stay FOREIGN KEY (stay_id) REFERENCES stays (id)
);

COMMENT ON TABLE stay_guests IS 'Stores detailed accompanying guest data for Alloggiati Web compliance';
COMMENT ON COLUMN stay_guests.stay_id IS 'Reference to the parent stay record';
