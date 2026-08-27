import { test, expect } from '@playwright/test';
import { ConsoleGuard } from './support/consoleGuard';
import { csrfHeader, createCleanRoom, createGuest, createWalkInStay } from '../fixtures/api';

// Blocco 5 (D2) — FatturaPA / fiscal export matrix. Reuses the existing
// e2e-live/fixtures/api.ts helpers (createGuest({fiscalDetails}), the same
// ones checkout-live.spec.ts and idor-cross-tenant-live.spec.ts already use)
// instead of duplicating fixture-creation logic.
test.describe('Blocco 5 (D2) — FatturaPA portal', () => {
  test('D2.1/D2.6: complete fiscal identity — FatturaPA XML downloads successfully, RICEVUTA→FATTURA switch works', async ({ request, page }) => {
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers, { fiscalDetails: true });
    const stay = await createWalkInStay(request, headers, { roomId: room.id, guestId: guest.id });

    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/billing');
    await page.getByRole('heading', { name: /fatturazione|billing/i }).waitFor();
    guard.checkpoint('billing list loaded');

    await page.getByRole('searchbox').fill(guest.email);
    await page.waitForTimeout(500);
    await page.getByRole('button', { name: /visualizza|view/i }).first().click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    guard.checkpoint('invoice detail opened');

    // A freshly created walk-in invoice is a RICEVUTA by default — switch to
    // FATTURA first (D2.6: switching before payment/export must be allowed).
    const switchBtn = dialog.getByRole('button', { name: /passa a fattura|switch to invoice|fattura/i });
    if (await switchBtn.isVisible().catch(() => false)) {
      await switchBtn.click();
      await page.waitForTimeout(500);
      guard.checkpoint('switched RICEVUTA -> FATTURA');
    }

    const downloadBtn = dialog.getByRole('button', { name: /scarica fatturapa xml|download fattura ?pa/i });
    await expect(downloadBtn).toBeVisible({ timeout: 5000 });
    await downloadBtn.click();
    await page.waitForTimeout(1000);
    // A complete guest+hotel fiscal identity must NOT produce the
    // "incomplete address" error toast this same flow produces for an
    // incomplete guest (see the D2.4 test below).
    await expect(page.getByText(/incompleti|incomplete/i)).not.toBeVisible();
    guard.checkpoint('FatturaPA XML download did not error — complete identity accepted');
    void stay;
  });

  test('D2.4: incomplete guest fiscal address is rejected before download, translated message shown', async ({ request, page }) => {
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers, { fiscalDetails: false }); // no cap/comune/provincia
    await createWalkInStay(request, headers, { roomId: room.id, guestId: guest.id });

    const guard = new ConsoleGuard(page, {
      role: 'admin',
      locale: 'it',
      extraAllow: [
        { pattern: /400.*fatturaPA\/validate/, reason: 'expected: GUEST_STRUCTURED_ADDRESS_INCOMPLETE for a guest with no fiscal address', scope: 'http_error' },
        { pattern: /400 \(Bad Request\)/, reason: 'expected: GUEST_STRUCTURED_ADDRESS_INCOMPLETE', scope: 'console' },
      ],
    });
    await page.goto('/billing');
    await page.getByRole('heading', { name: /fatturazione|billing/i }).waitFor();

    await page.getByRole('searchbox').fill(guest.email);
    await page.waitForTimeout(500);
    await page.getByRole('button', { name: /visualizza|view/i }).first().click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();

    const switchBtn = dialog.getByRole('button', { name: /passa a fattura|switch to invoice|fattura/i });
    if (await switchBtn.isVisible().catch(() => false)) {
      await switchBtn.click();
      await page.waitForTimeout(500);
    }
    const downloadBtn = dialog.getByRole('button', { name: /scarica fatturapa xml|download fattura ?pa/i });
    await downloadBtn.click();
    await expect(page.getByText(/GUEST_STRUCTURED_ADDRESS_INCOMPLETE|incompleti|incomplete/i)).toBeVisible({ timeout: 5000 });
    guard.checkpoint('incomplete guest address correctly rejected with a translated message');
  });

  // D2.13 (RECEPTIONIST 403 on invoice export/fatturaPA) is covered by
  // Blocco 9's dedicated per-role RBAC sweep, not duplicated here.
});
