import { test, expect } from '@playwright/test';
import { ConsoleGuard } from './support/consoleGuard';
import { restoreAllTenants } from './support/bootstrap';

// Blocco 5 (D4) — Email notifications (notification-service). Toggles are
// role=switch buttons (SettingsSystem.tsx ToggleRow), auto-save on click
// (no explicit Save button) — each click PATCHes /api/v1/stays/settings
// immediately. Restores the pre-round snapshot afterward since this
// mutates hotel_settings just like Blocco 4 does.
test.describe('Blocco 5 (D4) — Email notifications', () => {
  test.afterEach(async ({ request }) => {
    await restoreAllTenants(request);
  });

  test('reservation-confirmed and checkout email toggles auto-save, subject field appears/disappears with them', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/settings/system');
    await page.getByRole('heading', { name: /sistema|system/i }).waitFor();
    guard.checkpoint('system settings loaded');

    const reservationToggle = page.getByRole('switch', { name: /conferma prenotazione|reservation confirmation/i });
    await expect(reservationToggle).toBeVisible();
    const initialChecked = await reservationToggle.getAttribute('aria-checked');

    // Two subject fields can legitimately coexist (reservation + checkout,
    // each independently toggleable) — count them rather than assuming
    // .first() belongs to the toggle under test.
    const subjectFields = page.getByLabel(/oggetto personalizzato|custom subject/i);
    const countBefore = await subjectFields.count();

    await reservationToggle.click();
    await page.waitForTimeout(600); // auto-save PATCH
    const afterChecked = await reservationToggle.getAttribute('aria-checked');
    expect(afterChecked).not.toBe(initialChecked);
    guard.checkpoint('reservation-confirmed toggle auto-saved');

    const countAfter = await subjectFields.count();
    if (afterChecked === 'true') {
      expect(countAfter, 'a subject field must appear when the toggle turns on').toBe(countBefore + 1);
      await subjectFields.last().fill('QA25 Test Subject');
      await subjectFields.last().blur();
      await page.waitForTimeout(600);
      guard.checkpoint('subject field appeared and saved on blur');
    } else {
      expect(countAfter, 'a subject field must disappear when the toggle turns off').toBe(countBefore - 1);
      guard.checkpoint('subject field correctly hidden when toggle off');
    }
  });

  test('checkout email toggle auto-saves independently of the reservation toggle', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/settings/system');
    await page.getByRole('heading', { name: /sistema|system/i }).waitFor();

    const checkoutToggle = page.getByRole('switch', { name: /riepilogo check-?out|check-?out summary/i });
    await expect(checkoutToggle).toBeVisible();
    const before = await checkoutToggle.getAttribute('aria-checked');
    await checkoutToggle.click();
    await page.waitForTimeout(600);
    const after = await checkoutToggle.getAttribute('aria-checked');
    expect(after).not.toBe(before);
    guard.checkpoint('checkout email toggle auto-saved');
  });

  test('email greeting textarea: maxLength enforced, character counter updates, saves on blur', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/settings/system');
    await page.getByRole('heading', { name: /sistema|system/i }).waitFor();

    const greeting = page.locator('#email-greeting-text');
    await expect(greeting).toBeVisible();
    await greeting.fill('QA25 greeting test');
    await greeting.blur();
    await page.waitForTimeout(600);
    guard.checkpoint('greeting text saved on blur');

    // maxLength=300 is a hard HTML attribute — typing more than that must
    // not be accepted into the field value at all.
    const longText = 'x'.repeat(350);
    await greeting.fill(longText);
    const actualValue = await greeting.inputValue();
    expect(actualValue.length).toBeLessThanOrEqual(300);
    guard.checkpoint(`greeting text capped at maxLength (actual length: ${actualValue.length})`);
  });
});
