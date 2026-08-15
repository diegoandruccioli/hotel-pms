import { test as setup } from '@playwright/test';
import { LIVE_ADMIN, SEED_ADMIN } from './fixtures/hotel';

const STORAGE_STATE = 'e2e-live/.auth/admin.json';

// Logs in as the well-known seed admin, uses that session to create the
// live-suite's own admin user via the authenticated, RBAC-gated path
// (UserManagementController.createUser) — the public /register endpoint
// this used to rely on no longer exists (Finding #1: unauthenticated
// privilege escalation via client-supplied role/hotelId). Then logs in as
// that new admin, clears the mustChangePassword flag createUser() sets on
// every new account, configures the hotel's structured address, and saves
// the resulting httpOnly cookies as storageState for every spec in the
// "live" project.
//
// Done via context.request (not raw fetch) so the cookies Set-Cookie'd by
// each call land in the same context's cookie jar that gets persisted below
// — this is Playwright's documented pattern for httpOnly-cookie auth setup.
setup('authenticate as live-suite admin', async ({ page }) => {
    const seedLoginResponse = await page.request.post('/api/v1/auth/login', {
        data: { username: SEED_ADMIN.username, password: SEED_ADMIN.password },
    });
    if (seedLoginResponse.status() !== 200) {
        throw new Error(
            `Login failed for seed admin: ${seedLoginResponse.status()} ${await seedLoginResponse.text()}`,
        );
    }

    const seedCsrfCookie = (await page.context().cookies()).find((c) => c.name === 'csrf_token');
    if (!seedCsrfCookie) {
        throw new Error('csrf_token cookie missing right after seed admin login');
    }

    // Idempotent — 409 on a re-run means the user already exists, which is fine.
    const createUserResponse = await page.request.post('/api/v1/auth/users', {
        headers: { 'X-CSRF-Token': seedCsrfCookie.value },
        data: {
            username: LIVE_ADMIN.username,
            password: LIVE_ADMIN.password,
            email: LIVE_ADMIN.email,
            role: LIVE_ADMIN.role,
        },
    });
    if (createUserResponse.status() !== 201 && createUserResponse.status() !== 409) {
        throw new Error(
            `Unexpected status ${createUserResponse.status()} creating live-suite admin: `
                + (await createUserResponse.text()),
        );
    }

    const loginResponse = await page.request.post('/api/v1/auth/login', {
        data: { username: LIVE_ADMIN.username, password: LIVE_ADMIN.password },
    });
    if (loginResponse.status() !== 200) {
        throw new Error(`Login failed for live-suite admin: ${loginResponse.status()} ${await loginResponse.text()}`);
    }

    // createUser() flags every new account mustChangePassword=true — clear it
    // so the rest of this suite (and every other spec reusing this
    // storageState) isn't blocked by AuthenticationFilter's password-change
    // gate, which only allows /change-password, /me, /logout, /refresh.
    const loginBody = (await loginResponse.json()) as { mustChangePassword?: boolean };
    if (loginBody.mustChangePassword) {
        const adminCsrfCookie = (await page.context().cookies()).find((c) => c.name === 'csrf_token');
        if (!adminCsrfCookie) {
            throw new Error('csrf_token cookie missing right after live-suite admin login');
        }
        const changePasswordResponse = await page.request.post('/api/v1/auth/change-password', {
            headers: { 'X-CSRF-Token': adminCsrfCookie.value },
            data: { currentPassword: LIVE_ADMIN.password, newPassword: LIVE_ADMIN.password },
        });
        if (changePasswordResponse.status() !== 200) {
            throw new Error(
                `Failed to clear mustChangePassword for live-suite admin: ${changePasswordResponse.status()} `
                    + (await changePasswordResponse.text()),
            );
        }
    }

    // FatturaPAServiceImpl requires the HOTEL's own structured address
    // (cap/comune/provincia) to export a FATTURA — the seed hotel never had
    // one configured. A partial PUT (every other field null = "unchanged")
    // is safe to repeat on every setup run. change-password (if it ran)
    // rotates csrf_token, so re-read the cookie fresh here rather than
    // reusing adminCsrfCookie from above.
    const csrfCookie = (await page.context().cookies()).find((c) => c.name === 'csrf_token');
    if (!csrfCookie) {
        throw new Error('csrf_token cookie missing before configuring hotel settings');
    }
    const settingsResponse = await page.request.put('/api/v1/stays/settings', {
        headers: { 'X-CSRF-Token': csrfCookie.value },
        data: { cap: '00185', comune: 'Roma', provincia: 'RM' },
    });
    if (settingsResponse.status() !== 200) {
        throw new Error(
            `Failed to configure hotel structured address for FatturaPA: ${settingsResponse.status()} `
                + (await settingsResponse.text()),
        );
    }

    await page.context().storageState({ path: STORAGE_STATE });
});
