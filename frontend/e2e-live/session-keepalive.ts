import { request } from '@playwright/test';
import { LIVE_ADMIN } from './fixtures/hotel';
import { renameSync, statSync, writeFileSync } from 'node:fs';

// The access-token cookie lives 15 minutes (AuthController.ACCESS_COOKIE_MAX_AGE)
// and global.setup.ts logs in once, so a suite longer than that (about 30 min
// on a CI runner) loses its session half-way: every later request is a
// `401 No JWT cookie found`. Each test builds its context from the
// storageState file when it starts, so keeping that file fresh is enough, no
// spec has to know about it.
//
// Runs in the runner process (Playwright `globalSetup`), which outlives the
// worker. Once the setup project has written the file, it is refreshed
// through POST /api/v1/auth/refresh (CSRF-exempt, rotates all three cookies)
// before the access cookie can expire, falling back to a fresh login.

const STORAGE_STATE = 'e2e-live/.auth/admin.json';
const CHECK_EVERY_MS = 60_000;
const REFRESH_AFTER_MS = 8 * 60_000;

async function refreshStorageState(baseURL: string): Promise<void> {
    const context = await request.newContext({ baseURL, storageState: STORAGE_STATE });
    try {
        const refresh = await context.post('/api/v1/auth/refresh');
        if (refresh.status() !== 200) {
            // A refresh that timed out while auth-service was being stopped by a
            // resilience spec has already rotated the token server-side without us
            // seeing the new cookies, so the stored one is blacklisted for good.
            // A full login recovers from that.
            console.warn(`[keepalive] refresh failed (${refresh.status()}), logging in again`);
            const login = await context.post('/api/v1/auth/login', {
                data: { username: LIVE_ADMIN.username, password: LIVE_ADMIN.password },
            });
            if (login.status() !== 200) {
                console.warn(`[keepalive] login failed: ${login.status()}`);
                return;
            }
        }
        const state = await context.storageState();
        // Write-then-rename so a test starting right now never reads a half-written file.
        const tmp = `${STORAGE_STATE}.tmp`;
        writeFileSync(tmp, JSON.stringify(state));
        renameSync(tmp, STORAGE_STATE);
    } finally {
        await context.dispose();
    }
}

export default function globalSetup(): () => void {
    const baseURL = process.env.PLAYWRIGHT_LIVE_BASE_URL ?? 'http://localhost';
    let busy = false;
    const timer = setInterval(() => {
        if (busy) {
            return;
        }
        let ageMs: number;
        try {
            ageMs = Date.now() - statSync(STORAGE_STATE).mtimeMs;
        } catch {
            return; // setup project has not written the file yet
        }
        if (ageMs < REFRESH_AFTER_MS) {
            return;
        }
        busy = true;
        refreshStorageState(baseURL)
            .catch((error: unknown) => console.warn('[keepalive] session refresh error:', error))
            .finally(() => {
                busy = false;
            });
    }, CHECK_EVERY_MS);
    return () => clearInterval(timer);
}
