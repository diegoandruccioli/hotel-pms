import { test, expect, type Page } from '@playwright/test';
import { ConsoleGuard } from './support/consoleGuard';

// Blocco 3 — Restaurant area. MenuFormModal.tsx: name/category/price all
// required, price must be > 0 (menu_validation_required). Delete lives in the
// row's "Actions for <item>" menu and asks for confirmation in an in-app
// M3ConfirmDialog (role=dialog), not a native window.confirm.

/** Opens the row-actions menu of the menu item `name`, picks "Elimina voce" and returns the confirm dialog. */
async function openDeleteConfirm(page: Page, name: string) {
  await page.getByRole('button', { name: new RegExp(`^(Azioni per|Actions for) ${name}$`) }).click();
  await page.getByRole('menuitem', { name: /^(Elimina voce|Delete item)$/i }).click();
  const confirm = page.getByRole('dialog');
  await expect(confirm).toBeVisible();
  return confirm;
}

test.describe('Blocco 3 — Restaurant (menu CRUD)', () => {
  test('menu item: required-field + non-positive price validation blocks save', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/restaurant');
    await page.getByRole('heading', { name: /ristorante|restaurant/i }).waitFor();
    guard.checkpoint('restaurant page loaded');

    await page.getByRole('button', { name: /aggiungi voce|add.*item/i }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();

    await dialog.getByRole('button', { name: /^salva$|^save$/i }).click();
    await expect(dialog.getByRole('alert')).toBeVisible();
    await expect(dialog).toBeVisible();
    guard.checkpoint('empty submit blocked with inline error');

    await dialog.getByLabel(/^nome|^name/i).fill('QA25 Test Item');
    await dialog.getByLabel(/categoria|category/i).fill('QA25');
    await dialog.getByLabel(/prezzo|price/i).fill('0');
    await dialog.getByRole('button', { name: /^salva$|^save$/i }).click();
    await expect(dialog.getByRole('alert')).toBeVisible();
    guard.checkpoint('zero price blocked');
  });

  test('menu item: create then delete end-to-end (delete asks for in-app confirmation)', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/restaurant');
    await page.getByRole('heading', { name: /ristorante|restaurant/i }).waitFor();

    const uniqueName = `QA25 Item ${Date.now()}`;
    await page.getByRole('button', { name: /aggiungi voce|add.*item/i }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel(/^nome|^name/i).fill(uniqueName);
    await dialog.getByLabel(/categoria|category/i).fill('QA25');
    await dialog.getByLabel(/prezzo|price/i).fill('9.50');
    await dialog.getByRole('button', { name: /^salva$|^save$/i }).click();
    await expect(dialog).not.toBeVisible({ timeout: 5000 });
    guard.checkpoint('menu item created');

    await expect(page.getByText(uniqueName)).toBeVisible();

    const confirm = await openDeleteConfirm(page, uniqueName);
    await confirm.getByRole('button', { name: /^elimina$|^delete$/i }).click();
    await expect(confirm).not.toBeVisible({ timeout: 5000 });
    guard.checkpoint('delete confirmed in-app, no hang');
    await expect(page.getByText(uniqueName)).not.toBeVisible();
  });

  test('menu item: cancelling the delete confirmation keeps the item', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/restaurant');
    await page.getByRole('heading', { name: /ristorante|restaurant/i }).waitFor();

    const uniqueName = `QA25 KeepMe ${Date.now()}`;
    await page.getByRole('button', { name: /aggiungi voce|add.*item/i }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel(/^nome|^name/i).fill(uniqueName);
    await dialog.getByLabel(/categoria|category/i).fill('QA25');
    await dialog.getByLabel(/prezzo|price/i).fill('5.00');
    await dialog.getByRole('button', { name: /^salva$|^save$/i }).click();
    await expect(dialog).not.toBeVisible({ timeout: 5000 });

    const cancelDialog = await openDeleteConfirm(page, uniqueName);
    await cancelDialog.getByRole('button', { name: /^annulla$|^cancel$/i }).click();
    await expect(cancelDialog).not.toBeVisible({ timeout: 5000 });
    await expect(page.getByText(uniqueName)).toBeVisible(); // still there — cancelled, not deleted
    guard.checkpoint('cancelled confirm correctly kept the item');

    // Clean up for real this time.
    const cleanup = await openDeleteConfirm(page, uniqueName);
    await cleanup.getByRole('button', { name: /^elimina$|^delete$/i }).click();
    await expect(cleanup).not.toBeVisible({ timeout: 5000 });
  });
});
