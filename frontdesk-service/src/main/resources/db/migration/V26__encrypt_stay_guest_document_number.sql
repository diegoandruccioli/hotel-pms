-- ---------------------------------------------------------------
-- E23 (GDPR Art. 32): stay_guests.document_number is now encrypted at rest
-- by the application (StayGuestDocumentNumberConverter, AES-256-GCM). Widen
-- the column: ciphertext (IV + tag + hex/base64 encoding overhead) is much
-- longer than the plaintext document number it replaces. No data rewrite —
-- no index existed on this column to reconsider (unlike guest-service's
-- identity_documents.document_number, see V10 there).
-- ---------------------------------------------------------------

ALTER TABLE stay_guests
    ALTER COLUMN document_number TYPE VARCHAR(512);
