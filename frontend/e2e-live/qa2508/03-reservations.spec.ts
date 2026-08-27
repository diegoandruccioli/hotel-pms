import { test, expect } from '@playwright/test';
import { ConsoleGuard } from './support/consoleGuard';

// Blocco 3 — Reservations area, real backend. Selector patterns (aria-label
// "Delete {id}", check-in-btn-{id} testid, dialog Confirm button) confirmed
// against frontend/e2e/reservations.spec.ts (the mocked suite) and
// Reservations.tsx/ActionsCell.
test.describe('Blocco 3 — Reservations', () => {
  test('list loads, search filters, new-reservation navigates', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/reservations');
    await page.getByRole('heading', { name: /prenotazioni|reservations/i }).waitFor();
    guard.checkpoint('reservations list loaded');

    const search = page.getByRole('searchbox');
    await search.fill('zzz-no-such-guest-zzz');
    await page.waitForTimeout(500);
    await expect(page.getByRole('row')).toHaveCount(2); // header row + empty-state row
    guard.checkpoint('search with no matches shows empty state, no crash');
    await search.fill('');

    await page.getByRole('button', { name: /nuova prenotazione|new reservation/i }).click();
    await expect(page).toHaveURL(/\/reservations\/new/);
    guard.checkpoint('new-reservation navigates');
  });

  test('create a reservation end-to-end via the UI, then delete it', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/reservations/new');
    await page.getByRole('heading', { name: /nuova prenotazione|new reservation/i }).waitFor();
    guard.checkpoint('reservation form loaded');

    await page.getByPlaceholder(/cerca ospite|search guest/i).fill('Qa Round');
    await page.waitForTimeout(500);
    const suggestion = page.getByRole('button', { name: /qa round/i }).first();
    await expect(suggestion).toBeVisible({ timeout: 5000 });
    await suggestion.click();
    guard.checkpoint('guest selected');

    const dateInputs = page.locator('input[type=date]');
    const inDate = new Date();
    inDate.setMonth(inDate.getMonth() + 4);
    const outDate = new Date(inDate);
    outDate.setDate(outDate.getDate() + 2);
    const fmt = (d: Date) => d.toISOString().split('T')[0];
    await dateInputs.nth(0).fill(fmt(inDate));
    await dateInputs.nth(1).fill(fmt(outDate));

    const roomTile = page.getByRole('button', { name: /101/i }).first();
    await expect(roomTile).toBeVisible({ timeout: 8000 });
    await roomTile.click();
    guard.checkpoint('room selected');

    const confirmBtn = page.getByRole('button', { name: /conferma prenotazione|confirm reservation/i });
    await expect(confirmBtn).toBeEnabled({ timeout: 5000 });
    await confirmBtn.click();
    await expect(page).toHaveURL(/\/reservations$/, { timeout: 8000 });
    guard.checkpoint('reservation created, redirected to list');

    // Clean up: delete the reservation we just created (CONFIRMED is deletable).
    await page.getByRole('searchbox').fill('Qa Round');
    await page.waitForTimeout(500);
    const deleteBtn = page.getByRole('button', { name: /^delete |^elimina /i }).first();
    await expect(deleteBtn).toBeVisible({ timeout: 5000 });
    await deleteBtn.click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: /^conferma$|^confirm$/i }).click();
    await expect(dialog).not.toBeVisible({ timeout: 5000 });
    guard.checkpoint('reservation deleted');
  });
});
