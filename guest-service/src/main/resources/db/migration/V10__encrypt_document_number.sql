-- ---------------------------------------------------------------
-- E23 (GDPR Art. 32): identity_documents.document_number is now encrypted
-- at rest by the application (DocumentNumberConverter, AES-256-GCM). Two
-- schema changes follow from that, no data rewrite:
--
-- 1. Widen the column: ciphertext (IV + tag + hex/base64 encoding overhead)
--    is much longer than the plaintext document number it replaces.
-- 2. Drop idx_id_docs_document_number: a lookup index on this column is
--    already dead code (IdentityDocumentRepository carries a comment
--    recording that the query methods using it were removed, Point 7 item 2
--    — zero callers left), and once the column is ciphertext an equality
--    index on it can never match a plaintext search value again regardless.
-- ---------------------------------------------------------------

ALTER TABLE identity_documents
    ALTER COLUMN document_number TYPE VARCHAR(512);

DROP INDEX IF EXISTS idx_id_docs_document_number;
