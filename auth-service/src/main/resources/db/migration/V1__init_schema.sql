-- ============================================================
-- Flyway migration: V1__init_schema.sql
-- Service       : auth-service
-- Schema owner  : JPA entity UserAccount
-- Soft-delete   : active column + @SQLDelete / @SQLRestriction
-- Auditing      : created_at / updated_at via Spring Data @CreatedDate
-- UUID strategy : gen_random_uuid() (pg >= 13, no extension needed)
-- Enums         : Role → ADMIN, RECEPTIONIST, GUEST
-- Seed data     : Incorporates data.sql default ADMIN user
-- ============================================================

-- ---------------------------------------------------------------
-- user_account
-- Maps to: @Entity @Table(name = "user_account") UserAccount.java
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_account (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    username      VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(100) NOT NULL,
    role          VARCHAR(20) NOT NULL,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP   NOT NULL,
    updated_at    TIMESTAMP   NOT NULL,

    CONSTRAINT pk_user_account             PRIMARY KEY (id),
    CONSTRAINT uq_user_account_username    UNIQUE (username),
    CONSTRAINT uq_user_account_email       UNIQUE (email),
    CONSTRAINT chk_user_account_role       CHECK (role IN (
        'ADMIN', 'RECEPTIONIST', 'GUEST'
    ))
);

COMMENT ON TABLE  user_account               IS 'Authentication accounts for hotel staff and registered guests.';
COMMENT ON COLUMN user_account.id            IS 'Surrogate UUID primary key, generated server-side.';
COMMENT ON COLUMN user_account.username      IS 'Unique login handle. Max 50 characters.';
COMMENT ON COLUMN user_account.password_hash IS 'BCrypt-hashed password. Plain text MUST NEVER be stored here.';
COMMENT ON COLUMN user_account.email         IS 'Unique e-mail address. Max 100 characters.';
COMMENT ON COLUMN user_account.role          IS 'Authorization level: ADMIN | RECEPTIONIST | GUEST.';
COMMENT ON COLUMN user_account.active        IS 'Soft-delete flag. FALSE = account disabled; filtered by @SQLRestriction.';
COMMENT ON COLUMN user_account.created_at    IS 'Record creation timestamp, managed by Spring Data Auditing.';
COMMENT ON COLUMN user_account.updated_at    IS 'Last modification timestamp, managed by Spring Data Auditing.';

-- Partial index mirroring @SQLRestriction("active = true")
CREATE INDEX IF NOT EXISTS idx_user_account_active
    ON user_account (active)
    WHERE active = TRUE;

-- Index for username-based lookups (login flow – hot path)
CREATE INDEX IF NOT EXISTS idx_user_account_username
    ON user_account (username)
    WHERE active = TRUE;

-- Index for e-mail-based lookups (account recovery, duplicate check)
CREATE INDEX IF NOT EXISTS idx_user_account_email
    ON user_account (email)
    WHERE active = TRUE;

-- Index for role-based administrative queries
CREATE INDEX IF NOT EXISTS idx_user_account_role
    ON user_account (role)
    WHERE active = TRUE;

-- ---------------------------------------------------------------
-- Seed data – incorporated from data.sql
-- Creates the default ADMIN superuser for initial access.
-- Password: 'password' (BCrypt, cost factor 10)
-- ✅ Uses ON CONFLICT DO UPDATE so this is idempotent:
--    re-running the migration (e.g., baseline) does not fail.
-- ---------------------------------------------------------------
INSERT INTO user_account (
    id,
    username,
    password_hash,
    email,
    role,
    active,
    created_at,
    updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000000',
    'admin',
    '$2a$10$8aXe/PIDoC/tOecWVAMxsu57InT1n4F4Uq2ObRGB4W8DhGowDrbMi',
    'admin@hotel.com',
    'ADMIN',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE
    SET password_hash = EXCLUDED.password_hash,
        updated_at    = CURRENT_TIMESTAMP;
