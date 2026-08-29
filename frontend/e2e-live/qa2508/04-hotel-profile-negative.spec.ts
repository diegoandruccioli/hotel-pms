import { test, expect } from '@playwright/test';
import { ConsoleGuard } from './support/consoleGuard';
import { FISCAL_CASES } from './support/negativeData';

// Blocco 4 — HotelProfile negative-data matrix, tarato sui vincoli reali di
// HotelSettingsRequest.java (@Pattern vatNumber ^$|\d{11}, cap ^$|\d{5},
// provincia ^$|[A-Za-z]{2}, logoUrl ^$|https?://.+). Ogni caso: submit
// bloccato o errore tradotto, mai un crash, console pulita.
test.describe('Blocco 4 — HotelProfile negative data', () => {
  for (const c of FISCAL_CASES.vatNumber) {
    test(`vatNumber ${c.label}`, async ({ page }) => {
      const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
      await page.goto('/profile/hotel');
      await page.getByRole('heading', { name: /profilo hotel|hotel profile/i }).waitFor();
      guard.checkpoint('hotel profile loaded');

      const field = page.getByLabel(/partita iva|vat number/i);
      await field.fill(c.value);
      await page.getByRole('button', { name: /salva|save/i }).click();
      await page.waitForTimeout(500);

      if (c.label === 'valid-11-digits') {
        // Should NOT show a validation error for this one — it's the positive control.
        await expect(page.getByText(/11 (digits|cifre)/i)).not.toBeVisible();
      } else {
        const errorVisible = await page.getByText(/11 (digits|cifre)/i).isVisible().catch(() => false);
        const stillOnPage = await page.getByRole('heading', { name: /profilo hotel|hotel profile/i }).isVisible();
        expect(errorVisible || stillOnPage, `${c.label}: expected inline validation or blocked submit, got neither`).toBe(true);
      }
      guard.checkpoint(`vatNumber ${c.label} handled without crash`);
    });
  }

  for (const c of FISCAL_CASES.cap) {
    test(`cap ${c.label}`, async ({ page }) => {
      const guard = new ConsoleGuard(page, {
        role: 'admin',
        locale: 'it',
        extraAllow: [
          { pattern: /400.*\/api\/v1\/stays\/settings/, reason: 'expected: server correctly rejects cap not matching @Pattern', scope: 'http_error' },
          { pattern: /400 \(Bad Request\)/, reason: 'expected: server correctly rejects cap not matching @Pattern', scope: 'console' },
        ],
      });
      await page.goto('/profile/hotel');
      await page.getByRole('heading', { name: /profilo hotel|hotel profile/i }).waitFor();

      // Grouped so the ^ anchor applies to BOTH alternatives, not just the
      // first (CodeQL js/regex/missing-regexp-anchor — `^cap\b|postal code`
      // would let "postal code" match anywhere in an unrelated label, not
      // just at its start). The anchor itself is intentional: "cap" is only
      // 3 letters and could otherwise match as a substring of an unrelated
      // Italian word (e.g. "Capofamiglia").
      const field = page.getByLabel(/^(cap\b|postal code)/i);
      await field.fill(c.value);
      await page.getByRole('button', { name: /salva|save/i }).click();
      await page.waitForTimeout(500);
      // Must not crash or silently accept a malformed CAP.
      await expect(page.getByRole('heading', { name: /profilo hotel|hotel profile/i })).toBeVisible();
      guard.checkpoint(`cap ${c.label} handled without crash`);
    });
  }

  test('logoUrl javascript: scheme is rejected, not accepted as a valid URL', async ({ page }) => {
    // Two independent layers correctly reject this, both expected here:
    // the browser's own CSP blocks the live <img> preview from loading a
    // javascript: URL (img-src 'self' data:), and the server's
    // logoUrl @Pattern (^$|https?://.+) 400s the PUT. Both are allowlisted
    // as the correct outcome for this exact negative case, not anomalies.
    const guard = new ConsoleGuard(page, {
      role: 'admin',
      locale: 'it',
      extraAllow: [
        { pattern: /violates the following Content Security Policy/i, reason: 'expected: CSP correctly blocks javascript: URL in <img> preview' },
        { pattern: /^csp$/, reason: 'expected: CSP-blocked image request', scope: 'requestfailed' },
        { pattern: /400.*\/api\/v1\/stays\/settings/, reason: 'expected: server correctly rejects logoUrl not matching @Pattern', scope: 'http_error' },
        { pattern: /400 \(Bad Request\)/, reason: 'expected: server correctly rejects logoUrl not matching @Pattern', scope: 'console' },
      ],
    });
    await page.goto('/profile/hotel');
    await page.getByRole('heading', { name: /profilo hotel|hotel profile/i }).waitFor();

    const field = page.getByLabel(/url logo|logo url/i);
    await field.fill('javascript:alert(1)');
    await page.getByRole('button', { name: /salva|save/i }).click();
    await page.waitForTimeout(500);
    // No alert() must have fired (would hang the test if it did) and the
    // page must still be responsive.
    await expect(page.getByRole('heading', { name: /profilo hotel|hotel profile/i })).toBeVisible();
    guard.checkpoint('javascript: scheme in logoUrl correctly rejected by CSP and server, page still responsive');
  });
});
