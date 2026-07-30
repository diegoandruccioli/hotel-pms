// Shared fixtures for the live E2E suite (Fase 7). This is the hotelId seeded
// by every service's Flyway baseline for local/dev environments — not a
// secret, just a well-known dev tenant id used throughout this repo's manual
// testing this session (see backup/SUMMARY.md).
export const SEED_HOTEL_ID = '00000000-0000-0000-0000-000000000001';

export const LIVE_ADMIN = {
    username: 'e2e-live-admin',
    password: 'E2eLiveAdmin!2026#run',
    email: 'e2e-live-admin@hotel-pms.local',
    role: 'ADMIN',
    hotelId: SEED_HOTEL_ID,
};
