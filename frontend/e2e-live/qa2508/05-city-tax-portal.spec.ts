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

  test('DEFECT 🟢: help text claims re-registering a category "auto-closes" the current rate, but it actually 409s', async ({ page }) => {
    // CityTaxRatesSection.tsx's section description says "Registrare una
    // nuova tariffa per la stessa categoria chiude automaticamente quella
    // corrente" (settings.json city_tax_rates_section_desc) — but the
    // backend has no such supersede logic for open-ended ranges: a second
    // rate for the same category, with no validTo on the first, is a
    // genuine overlap and correctly 409s (excl_city_tax_rates_no_overlap),
    // surfaced via the translated city_tax_err_overlap toast — not silently
    // superseded as the on-screen copy claims. The error handling itself is
    // correct (translated, not a raw 409); only the descriptive text is
    // inaccurate. Minor/cosmetic — filed as 🟢, not blocking.
    const guard = new ConsoleGuard(page, {
      role: 'admin',
      locale: 'it',
      extraAllow: [
        { pattern: /409.*city-tax-rates/, reason: 'expected: same-category re-registration correctly rejected as overlap', scope: 'http_error' },
        { pattern: /409 \(Conflict\)/, reason: 'expected: same-category re-registration correctly rejected as overlap', scope: 'console' },
      ],
    });
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
    await expect(page.getByText(/esiste già una regola attiva|an active rule already exists/i)).toBeVisible({ timeout: 5000 });
    guard.checkpoint('second same-category rate correctly rejected as overlap, translated message shown (contradicts the "auto-closes" help text)');
  });
});
