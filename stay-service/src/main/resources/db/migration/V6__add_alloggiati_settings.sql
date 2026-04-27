ALTER TABLE stays
    ADD COLUMN alloggiati_sent BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE hotel_settings (
    hotel_id             UUID    NOT NULL,
    alloggiati_auto_send BOOLEAN NOT NULL DEFAULT false,
    created_at           TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP,
    CONSTRAINT pk_hotel_settings PRIMARY KEY (hotel_id)
);
