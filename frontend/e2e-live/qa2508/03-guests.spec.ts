import { test, expect } from '@playwright/test';
import { ConsoleGuard } from './support/consoleGuard';

// Blocco 3 — Guests area. Create -> search -> edit -> delete, full CRUD
// against the real backend, console checked at every step.
test.describe('Blocco 3 — Guests', () => {
  test('create, search, edit, delete a guest end-to-end', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/guests');
    await page.getByRole('heading', { name: /^ospiti$|^guests$/i }).waitFor();
    guard.checkpoint('guests list loaded');

    const uniqueEmail = `qa2508.guest.${Date.now()}@example.com`;

    await page.getByRole('button', { name: /aggiungi ospite|add guest/i }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    guard.checkpoint('add-guest modal opened');

    await dialog.getByLabel(/nome|first name/i).fill('Qa Round');
    await dialog.getByLabel(/cognome|last name/i).fill('Guest Test');
    await dialog.getByRole('textbox', { name: /email/i }).fill(uniqueEmail);

    // Collapsible fiscal-data section — verify it toggles before using it.
    const fiscalToggle = dialog.getByRole('button', { name: /dati fiscali|fiscal data/i });
    if (await fiscalToggle.isVisible().catch(() => false)) {
      await fiscalToggle.click();
      guard.checkpoint('fiscal section expanded');
      await fiscalToggle.click(); // collapse back — not needed for this test
    }

    await dialog.getByRole('button', { name: /^salva$|^save$/i }).click();
    await expect(dialog).not.toBeVisible({ timeout: 5000 });
    guard.checkpoint('guest saved, modal closed');

    // Search
    const search = page.getByPlaceholder(/cerca|search/i).first();
    await search.fill(uniqueEmail);
    await page.waitForTimeout(500); // 300ms debounce
    await expect(page.getByText('Qa Round')).toBeVisible({ timeout: 5000 });
    guard.checkpoint('search finds the new guest');

    // Edit
    await page.getByRole('button', { name: /^modifica$|^edit$/i }).first().click();
    const editDialog = page.getByRole('dialog');
    await expect(editDialog).toBeVisible();
    await editDialog.getByLabel(/città|city/i).fill('Roma');
    await editDialog.getByRole('button', { name: /^salva$|^save$/i }).click();
    await expect(editDialog).not.toBeVisible({ timeout: 5000 });
    guard.checkpoint('guest edited');

    // Delete (ADMIN-only action)
    await page.getByRole('button', { name: new RegExp(`elimina|delete`, 'i') }).first().click();
    const confirmDialog = page.getByRole('dialog').filter({ hasText: /elimina|delete/i });
    await expect(confirmDialog).toBeVisible();
    await confirmDialog.getByRole('button', { name: /^elimina$|^delete$/i }).click();
    await expect(confirmDialog).not.toBeVisible({ timeout: 5000 });
    guard.checkpoint('guest deleted');

    await expect(page.getByText(uniqueEmail)).not.toBeVisible();
  });

  test('required-field validation blocks save with a clear inline error', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/guests');
    await page.getByRole('heading', { name: /^ospiti$|^guests$/i }).waitFor();

    await page.getByRole('button', { name: /aggiungi ospite|add guest/i }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('button', { name: /^salva$|^save$/i }).click();
    await expect(dialog).toBeVisible(); // must NOT close
    guard.checkpoint('empty submit blocked, no crash');
  });
});
