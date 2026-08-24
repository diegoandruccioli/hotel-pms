// Shared fixtures for the live E2E suite (Fase 7). This is the hotelId seeded
// by every service's Flyway baseline for local/dev environments — not a
// secret, just a well-known dev tenant id used throughout this repo's manual
// testing this session (see backup/SUMMARY.md).
export const SEED_HOTEL_ID = '00000000-0000-0000-0000-000000000001';

// The default seed admin (V1__init_schema.sql), used to bootstrap the
// live-suite's own admin user via UserManagementController.createUser
// instead of the public /register endpoint (removed — Finding #1, CRITICAL).
//
// 2026-08-24: the seed default (admin/password) no longer matches this
// stack's DB — a prior QA session's admin login had already rotated away
// from it (must_change_password was false with an unknown password before
// this run reset it). Reset via psql to a known value, then rotated through
// the real forced-first-login change-password flow to this final password —
// exercising that flow for real, not just documenting it. Local Docker dev
// stack only; not a production credential.
export const SEED_ADMIN = {
    username: 'admin',
    password: 'QaAdmin2026!!RotatedOK',
};

// A second, independent tenant for cross-tenant IDOR/RBAC checks
// (idor-cross-tenant-live.spec.ts). Seeded directly in
// V7__seed_second_hotel_admin_for_e2e_tests.sql — not created on the fly
// via /register, which no longer exists.
export const OTHER_HOTEL_ID = '99999999-9999-9999-9999-999999999999';

export const OTHER_HOTEL_ADMIN = {
    username: 'e2e-live-other-hotel-admin',
    password: 'password',
};

export const LIVE_ADMIN = {
    username: 'e2e-live-admin',
    password: 'E2eLiveAdmin!2026#run',
    email: 'e2e-live-admin@hotel-pms.local',
    role: 'ADMIN',
    hotelId: SEED_HOTEL_ID,
};
