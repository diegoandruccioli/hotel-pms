import { test as setup } from '@playwright/test';
import { LIVE_ADMIN } from './fixtures/hotel';

const STORAGE_STATE = 'e2e-live/.auth/admin.json';

// Registers the live-suite admin user against the real auth-service (idempotent
// — 409 on a re-run means the user already exists from a prior run, which is
// fine), logs in through the real UI-facing API, then saves the resulting
// httpOnly cookies as storageState for every spec in the "live" project.
//
// Done via context.request (not raw fetch) so the cookies Set-Cookie'd by
// login land in the same context's cookie jar that gets persisted below —
// this is Playwright's documented pattern for httpOnly-cookie auth setup.
setup('authenticate as live-suite admin', async ({ page }) => {
    const registerResponse = await page.request.post('/api/v1/auth/register', {
        data: {
            username: LIVE_ADMIN.username,
            password: LIVE_ADMIN.password,
            email: LIVE_ADMIN.email,
            role: LIVE_ADMIN.role,
            hotelId: LIVE_ADMIN.hotelId,
        },
    });
    if (registerResponse.status() !== 201 && registerResponse.status() !== 409) {
        throw new Error(
            `Unexpected status ${registerResponse.status()} registering live-suite admin: ` +
                (await registerResponse.text()),
        );
    }

    const loginResponse = await page.request.post('/api/v1/auth/login', {
        data: { username: LIVE_ADMIN.username, password: LIVE_ADMIN.password },
    });
    if (loginResponse.status() !== 200) {
        throw new Error(`Login failed for live-suite admin: ${loginResponse.status()} ${await loginResponse.text()}`);
    }

    await page.context().storageState({ path: STORAGE_STATE });
});
