-- ============================================================
-- Flyway migration: V1__init_schema.sql
-- Service       : guest-service
-- Schema owner  : JPA entities Guest and IdentityDocument
-- Soft-delete   : active column + @SQLDelete / @SQLRestriction
-- Auditing      : created_at / updated_at via Spring Data @CreatedDate
-- UUID strategy : gen_random_uuid() (pg >= 13, no extension needed)
-- Enums         : DocumentType → PASSPORT, DRIVERS_LICENSE, NATIONAL_ID, OTHER
-- ============================================================

-- ---------------------------------------------------------------
-- guests
-- Maps to: @Entity @Table(name = "guests") Guest.java
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS guests (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    email         VARCHAR(150) NOT NULL,
    phone         VARCHAR(20),
    address       VARCHAR(255),
    city          VARCHAR(50),
    country       VARCHAR(50),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,

    CONSTRAINT pk_guests             PRIMARY KEY (id),
    CONSTRAINT uq_guests_email       UNIQUE (email)
);

COMMENT ON TABLE  guests               IS 'Hotel guest profiles used for check-in and Alloggiati Web reporting.';
COMMENT ON COLUMN guests.id            IS 'Surrogate UUID primary key, generated server-side.';
COMMENT ON COLUMN guests.first_name    IS 'Guest given name. Max 100 characters.';
COMMENT ON COLUMN guests.last_name     IS 'Guest family name. Max 100 characters.';
COMMENT ON COLUMN guests.date_of_birth IS 'Required for Italian Alloggiati Web police reporting (art. 109 TULPS).';
COMMENT ON COLUMN guests.email         IS 'Unique contact e-mail address.';
COMMENT ON COLUMN guests.phone         IS 'Optional contact phone number.';
COMMENT ON COLUMN guests.address       IS 'Optional street address of the guest.';
COMMENT ON COLUMN guests.city          IS 'Optional city of residence.';
COMMENT ON COLUMN guests.country       IS 'Optional ISO country code / country name.';
COMMENT ON COLUMN guests.active        IS 'Soft-delete flag. FALSE = logically deleted; filtered by @SQLRestriction.';
COMMENT ON COLUMN guests.created_at    IS 'Record creation timestamp, managed by Spring Data Auditing.';
COMMENT ON COLUMN guests.updated_at    IS 'Last modification timestamp, managed by Spring Data Auditing.';

-- Partial index mirroring @SQLRestriction("active = true")
CREATE INDEX IF NOT EXISTS idx_guests_active
    ON guests (active)
    WHERE active = TRUE;

-- Index for case-sensitive e-mail lookups
CREATE INDEX IF NOT EXISTS idx_guests_email
    ON guests (email)
    WHERE active = TRUE;

-- Index for name-based searches
CREATE INDEX IF NOT EXISTS idx_guests_last_name
    ON guests (last_name)
    WHERE active = TRUE;

-- ---------------------------------------------------------------
-- identity_documents
-- Maps to: @Entity @Table(name = "identity_documents") IdentityDocument.java
-- Enum    : DocumentType → PASSPORT | DRIVERS_LICENSE | NATIONAL_ID | OTHER
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS identity_documents (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    guest_id        UUID        NOT NULL,
    document_type   VARCHAR(30) NOT NULL,
    document_number VARCHAR(100) NOT NULL,
    issue_date      DATE        NOT NULL,
    expiry_date     DATE        NOT NULL,
    issuing_country VARCHAR(100),
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP   NOT NULL,

    CONSTRAINT pk_identity_documents         PRIMARY KEY (id),
    CONSTRAINT fk_id_docs_guest              FOREIGN KEY (guest_id)
                                                 REFERENCES guests (id)
                                                 ON UPDATE CASCADE
                                                 ON DELETE CASCADE,
    CONSTRAINT chk_id_docs_document_type     CHECK (document_type IN (
        'PASSPORT', 'DRIVERS_LICENSE', 'NATIONAL_ID', 'OTHER'
    )),
    CONSTRAINT chk_id_docs_expiry_after_issue CHECK (expiry_date > issue_date)
);

COMMENT ON TABLE  identity_documents                 IS 'Identity documents belonging to a guest (passport, driving licence, etc.).';
COMMENT ON COLUMN identity_documents.id              IS 'Surrogate UUID primary key, generated server-side.';
COMMENT ON COLUMN identity_documents.guest_id        IS 'FK → guests.id; the owning guest profile.';
COMMENT ON COLUMN identity_documents.document_type   IS 'Kind of document: PASSPORT | DRIVERS_LICENSE | NATIONAL_ID | OTHER.';
COMMENT ON COLUMN identity_documents.document_number IS 'Official document serial / number.';
COMMENT ON COLUMN identity_documents.issue_date      IS 'Date the document was issued by the authority.';
COMMENT ON COLUMN identity_documents.expiry_date     IS 'Date the document expires. Must be after issue_date.';
COMMENT ON COLUMN identity_documents.issuing_country IS 'Country that issued the document (ISO-3166 name or code).';
COMMENT ON COLUMN identity_documents.active          IS 'Soft-delete flag. FALSE = logically deleted; filtered by @SQLRestriction.';
COMMENT ON COLUMN identity_documents.created_at      IS 'Record creation timestamp, managed by Spring Data Auditing.';
COMMENT ON COLUMN identity_documents.updated_at      IS 'Last modification timestamp, managed by Spring Data Auditing.';

-- Partial index mirroring @SQLRestriction("active = true")
CREATE INDEX IF NOT EXISTS idx_id_docs_active
    ON identity_documents (active)
    WHERE active = TRUE;

-- Index on FK for JOIN queries and cascade checks
CREATE INDEX IF NOT EXISTS idx_id_docs_guest_id
    ON identity_documents (guest_id);

-- Index to facilitate lookups by document number (e.g., border-control check)
CREATE INDEX IF NOT EXISTS idx_id_docs_document_number
    ON identity_documents (document_number)
    WHERE active = TRUE;
