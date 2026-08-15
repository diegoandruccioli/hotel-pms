-- ============================================================
-- Flyway migration: V7__seed_second_hotel_admin_for_e2e_tests.sql
-- Service       : auth-service
-- Purpose       : Second-tenant ADMIN fixture for the live E2E suite
--                 (frontend/e2e-live/idor-cross-tenant-live.spec.ts), which
--                 needs a real, independent identity in a DIFFERENT hotel to
--                 exercise cross-tenant IDOR/RBAC checks against the real
--                 backend. Previously created on the fly via the public
--                 POST /api/v1/auth/register endpoint — removed as part of
--                 closing Finding #1 (CRITICAL, unauthenticated privilege
--                 escalation via self-registration with an arbitrary
--                 role/hotelId). Same pattern as the default seed admin in
--                 V1__init_schema.sql, one row, one other tenant.
--                 (feature/secure-coding-hardening)
-- ============================================================

INSERT INTO user_account (
    id,
    username,
    password_hash,
    email,
    role,
    hotel_id,
    active,
    must_change_password,
    created_at,
    updated_at
)
VALUES (
    'ffffffff-ffff-ffff-ffff-ffffffffffff',
    'e2e-live-other-hotel-admin',
    '{bcrypt}$2a$10$8aXe/PIDoC/tOecWVAMxsu57InT1n4F4Uq2ObRGB4W8DhGowDrbMi',
    'e2e-live-other-hotel-admin@hotel-pms.local',
    'ADMIN',
    '99999999-9999-9999-9999-999999999999',
    TRUE,
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash;
