-- Per-hotel toggles and customization for transactional email notifications
-- (C1 gap: owner had no way to disable/customize confirmation and checkout
-- emails). Defaults preserve current always-on behaviour.
ALTER TABLE hotel_settings ADD COLUMN send_reservation_confirmed_email BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE hotel_settings ADD COLUMN send_checkout_email BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE hotel_settings ADD COLUMN email_subject_reservation_confirmed VARCHAR(200);
ALTER TABLE hotel_settings ADD COLUMN email_subject_checkout VARCHAR(200);
ALTER TABLE hotel_settings ADD COLUMN email_greeting_text VARCHAR(300);
