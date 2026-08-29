import { test, expect } from '@playwright/test';
import { ConsoleGuard } from './support/consoleGuard';

// Blocco 3 — Rooms / Room Types area. Tab structure and modal fields
// confirmed via Chrome MCP walkthrough (2026-08-27): "Nuova Tipologia"
// requires Nome, shows "Campo obbligatorio" inline on empty submit.
test.describe('Blocco 3 — Rooms & Room Types', () => {
  test('tab switch, room type create validation, create+delete a room type', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/rooms');
    await page.getByRole('heading', { name: /inventario|inventory/i }).waitFor();
    guard.checkpoint('rooms page loaded');

    await page.getByRole('button', { name: /tipologie|room categories/i }).click();
    await expect(page.getByRole('heading', { name: /^tipologie$|^room categories$/i })).toBeVisible();
    guard.checkpoint('switched to room types tab');

    await page.getByRole('button', { name: /nuova tipologia|add category/i }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();

    await dialog.getByRole('button', { name: /^salva$|^save$/i }).click();
    await expect(dialog.getByText(/campo obbligatorio|required/i)).toBeVisible();
    await expect(dialog).toBeVisible(); // still open, not submitted
    guard.checkpoint('empty submit blocked with inline error');

    const uniqueName = `QA25 RoomType ${Date.now()}`;
    await dialog.getByLabel(/^nome|^name/i).fill(uniqueName);
    await dialog.getByRole('button', { name: /^salva$|^save$/i }).click();
    await expect(dialog).not.toBeVisible({ timeout: 5000 });
    guard.checkpoint('room type created');

    await expect(page.getByText(uniqueName)).toBeVisible();

    // Clean up via edit modal's inline delete-confirm footer.
    const row = page.locator('tr').filter({ hasText: uniqueName });
    await row.getByRole('button', { name: /^modifica$|^edit$/i }).click();
    const editDialog = page.getByRole('dialog');
    await expect(editDialog).toBeVisible();
    await editDialog.getByRole('button', { name: /^elimina$|^delete$/i }).click();
    await editDialog.getByRole('button', { name: /^conferma$|^confirm$/i }).click();
    await expect(editDialog).not.toBeVisible({ timeout: 5000 });
    guard.checkpoint('room type deleted');
    await expect(page.getByText(uniqueName)).not.toBeVisible();
  });

  test('rooms tab: availability filter toggles without console errors', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/rooms');
    await page.getByRole('heading', { name: /inventario|inventory/i }).waitFor();

    const filterChip = page.getByRole('button', { name: /disponibili oggi|available today/i });
    await filterChip.click();
    await page.waitForTimeout(500);
    guard.checkpoint('availability filter toggled on');
    await filterChip.click();
    await page.waitForTimeout(500);
    guard.checkpoint('availability filter toggled off');
  });
});
