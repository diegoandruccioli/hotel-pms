import { test, expect } from '@playwright/test';
import { ConsoleGuard } from './support/consoleGuard';

// Blocco 5 (D3) — Imposta di soggiorno / city tax settings matrix.
// SettingsCityTax.tsx: HotelCategorySection + CityTaxRatesSection, zod
// client-side validation + server 400/409 mapped to translated toasts
// (city_tax_err_comune_not_configured / city_tax_err_overlap).
test.describe('Blocco 5 (D3) — City tax settings', () => {
  test('rate form: client-side boundary validation blocks bad input before any request', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/settings/city-tax');
    await page.getByRole('heading', { name: /imposta di soggiorno|tourist tax/i }).waitFor();
    guard.checkpoint('city tax settings loaded');

    // exemptUnderAge = 121 (> AGE_MAX 120)
    await page.getByLabel(/categoria \*|category \*/i).nth(1).fill(`QA25-${Date.now()}`.slice(0, 20));
    await page.getByLabel(/importo a notte \*|amount per night \*/i).fill('3.50');
    await page.getByLabel(/esenzione et|exempt.*age/i).fill('121');
    await page.getByLabel(/valido dal \*|valid from \*/i).nth(1).fill('2026-01-01');
    await page.getByRole('button', { name: /aggiungi tariffa|add rate/i }).click();
    await page.waitForTimeout(300);

    // Must still be on the page with an inline error, not a submitted request.
    await expect(page.getByRole('heading', { name: /imposta di soggiorno|tourist tax/i })).toBeVisible();
    guard.checkpoint('exemptUnderAge=121 blocked client-side');
  });

  test('rate form: negative amountPerNight blocked client-side', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/settings/city-tax');
    await page.getByRole('heading', { name: /imposta di soggiorno|tourist tax/i }).waitFor();

    await page.getByLabel(/categoria \*|category \*/i).nth(1).fill(`QA25-${Date.now()}`.slice(0, 20));
    await page.getByLabel(/importo a notte \*|amount per night \*/i).fill('-1');
    await page.getByLabel(/valido dal \*|valid from \*/i).nth(1).fill('2026-01-01');
    await page.getByRole('button', { name: /aggiungi tariffa|add rate/i }).click();
    await page.waitForTimeout(300);
    await expect(page.getByRole('heading', { name: /imposta di soggiorno|tourist tax/i })).toBeVisible();
    guard.checkpoint('negative amountPerNight blocked client-side');
  });

  test('re-registering a category auto-closes the current rate, as the help text says', async ({ page }) => {
    // Round 2026-08-25 filed this as a 🟢 defect: CityTaxRatesSection.tsx's description says
    // "Registrare una nuova tariffa per la stessa categoria chiude automaticamente quella
    // corrente", yet the backend 409'd. Fixed since (3214867, "auto-close reale della
    // tariffa"): a later validFrom now closes the open rate, so the copy is accurate and this
    // test pins the fixed behaviour instead of the old overlap rejection.
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/settings/city-tax');
    await page.getByRole('heading', { name: /imposta di soggiorno|tourist tax/i }).waitFor();

    const category = `QA25${Date.now()}`.slice(0, 20);
    const addRate = async (validFrom: string) => {
      await page.getByLabel(/categoria \*|category \*/i).nth(1).fill(category);
      await page.getByLabel(/importo a notte \*|amount per night \*/i).fill('2.00');
      await page.getByLabel(/valido dal \*|valid from \*/i).nth(1).fill(validFrom);
      await page.getByRole('button', { name: /aggiungi tariffa|add rate/i }).click();
      await page.waitForTimeout(800);
    };

    await addRate('2026-01-01');
    await expect(page.getByRole('cell', { name: category }).first()).toBeVisible();
    guard.checkpoint('first rate created and listed');

    await addRate('2026-02-01');
    await expect(page.getByRole('cell', { name: category })).toHaveCount(2, { timeout: 5000 });
    await expect(page.getByText(/esiste già una regola attiva|an active rule already exists/i)).toHaveCount(0);
    guard.checkpoint('second same-category rate accepted, first one auto-closed');
  });
});
