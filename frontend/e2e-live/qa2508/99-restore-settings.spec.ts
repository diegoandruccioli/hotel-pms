import { test, expect } from '@playwright/test';
import { restoreAllTenants } from './support/bootstrap';

// Runs last (99- prefix) to restore hotel_settings to the pre-round
// snapshot taken in 00-bootstrap.spec.ts — Blocco 4's negative-data tests
// (vatNumber/cap/logoUrl) intentionally mutate these fields.
test.describe('Blocco 9 (safety) — restore hotel settings', () => {
  test('restore hotel_settings to pre-round snapshot', async ({ request }) => {
    await restoreAllTenants(request);
    const settings = await request.get('/api/v1/stays/settings');
    expect(settings.status()).toBe(200);
  });
});
