-- P8: per-hotel Alloggiati Web credentials, so onboarding a new hotel no
-- longer requires editing the global alloggiati.web.* env vars and
-- restarting the container. Password and WsKey are stored AES-GCM
-- encrypted (see AlloggiatiCredentialEncryptor) — username is a portal
-- login name, not a secret, and stays plain.
-- All three are nullable: a hotel that has not configured its own
-- credentials yet falls back to the legacy global env vars at submission
-- time (single-hotel pilot behavior, unchanged).
ALTER TABLE hotel_settings
    ADD COLUMN alloggiati_username VARCHAR(100),
    ADD COLUMN alloggiati_password_encrypted TEXT,
    ADD COLUMN alloggiati_ws_key_encrypted TEXT;
