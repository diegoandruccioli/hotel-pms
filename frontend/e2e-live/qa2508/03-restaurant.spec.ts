import { test, expect } from '@playwright/test';
import { ConsoleGuard } from './support/consoleGuard';

// Blocco 3 — Restaurant area. MenuFormModal.tsx: name/category/price all
// required, price must be > 0 (menu_validation_required), delete uses a
// native window.confirm (Restaurant.tsx:172) — must be handled via
// page.on('dialog') or the automation hangs.
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

  test('menu item: create then delete end-to-end (delete uses a native confirm dialog)', async ({ page }) => {
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

    // Delete triggers window.confirm(Restaurant.tsx:172) — accept it. Wrap
    // in guard.expectDialog() so the ConsoleGuard doesn't flag this
    // deliberate, handled dialog as an anomaly.
    await guard.expectDialog(async () => {
      page.once('dialog', (d) => d.accept());
      await page.getByRole('button', { name: new RegExp(`elimina.*${uniqueName}|delete.*${uniqueName}`, 'i') }).click();
      await page.waitForTimeout(800);
    });
    guard.checkpoint('delete confirmed via native dialog, no hang');
    await expect(page.getByText(uniqueName)).not.toBeVisible();
  });

  test('menu item: dismissing the native delete confirm keeps the item', async ({ page }) => {
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

    await guard.expectDialog(async () => {
      page.once('dialog', (d) => d.dismiss());
      await page.getByRole('button', { name: new RegExp(`elimina.*${uniqueName}|delete.*${uniqueName}`, 'i') }).click();
      await page.waitForTimeout(800);
    });
    await expect(page.getByText(uniqueName)).toBeVisible(); // still there — dismissed, not deleted
    guard.checkpoint('dismissed confirm correctly kept the item');

    // Clean up for real this time.
    await guard.expectDialog(async () => {
      page.once('dialog', (d) => d.accept());
      await page.getByRole('button', { name: new RegExp(`elimina.*${uniqueName}|delete.*${uniqueName}`, 'i') }).click();
      await page.waitForTimeout(800);
    });
  });
});
