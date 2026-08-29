import { test, expect } from '@playwright/test';
import { ensureAllQa25Users } from './support/roles';
import { snapshotAllTenants, ensureFixtureIds } from './support/bootstrap';
import { SEED_HOTEL_ID } from '../fixtures/hotel';

// Blocco 0 of the 2026-08-25 exhaustive round (plan:
// C:\Users\Diego\.claude\plans\viglio-che-ora-vai-composed-brooks.md).
// Runs first (00- prefix, workers:1 in playwright-live.config.ts => file
// order matters) so every later qa2508 spec can assume: the three qa2508
// role accounts exist and can log in, and hotel A's settings are snapshotted
// for restoration at the end of the round.
test.describe('Blocco 0 — bootstrap', () => {
  test('qa2508 role accounts exist and hotel settings are snapshotted', async ({ request }) => {
    await ensureAllQa25Users(request, test.info().project.use.baseURL as string | undefined);
    await snapshotAllTenants(request);

    const settings = await request.get('/api/v1/stays/settings');
    expect(settings.status()).toBe(200);
    const body = await settings.json();
    expect(body.hotelId ?? SEED_HOTEL_ID).toBeTruthy();

    const fixtures = await ensureFixtureIds(request);
    expect(fixtures.reservationId).toBeTruthy();
    expect(fixtures.quotationId).toBeTruthy();
  });
});
