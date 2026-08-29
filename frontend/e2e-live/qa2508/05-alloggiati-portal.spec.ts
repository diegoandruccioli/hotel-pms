import { test, expect } from '@playwright/test';
import { ConsoleGuard } from './support/consoleGuard';

// Blocco 5 (D1) — Alloggiati Web portal, started with the regression for a
// real bug found 2026-08-27: downloadAlloggiatiReport/Json fired the
// download iframe and unconditionally showed a success toast, with no
// verification that the underlying request actually succeeded. Unlike
// billingService.downloadFatturaPAXml (validate-then-download in the same
// codebase), a failed Alloggiati report — wrong credentials, malformed
// guest data, a backend error — was reported to hotel staff as a success,
// silently hiding a TULPS art. 109 compliance failure. Fixed in
// stayService.ts by awaiting a real GET before the iframe.
test.describe('Blocco 5 (D1) — Alloggiati portal', () => {
  test('failed report generation shows an error toast, not a false success', async ({ page }) => {
    const guard = new ConsoleGuard(page, {
      role: 'admin',
      locale: 'it',
      extraAllow: [
        { pattern: /502.*reports\/alloggiati/, reason: 'deliberately injected fault for this test', scope: 'http_error' },
        { pattern: /502 \(Bad Gateway\)/, reason: 'deliberately injected fault for this test', scope: 'console' },
      ],
    });
    await page.goto('/stays');
    await page.getByRole('heading', { name: /soggiorni|stays/i }).waitFor();
    guard.checkpoint('stays page loaded');

    // Force the underlying report GET to fail — same endpoint the fixed
    // downloadAlloggiatiReport() now awaits before triggering the iframe.
    await page.route('**/api/v1/stays/reports/alloggiati?date=*', (route) => {
      if (route.request().method() !== 'GET') return route.continue();
      route.fulfill({ status: 502, contentType: 'application/json', body: JSON.stringify({ detail: 'EXTERNAL_SERVICE_ERROR' }) });
    });

    await page.locator('#generate-alloggiati-btn').click();
    // The toast surfaces the raw backend detail string when getErrorMessage
    // has no better fallback (AlloggiatiReportSection.tsx:37-39) — scope to
    // the toast region specifically, since "failed" alone also matches
    // unrelated "Email failed" badges already on the page from other stays.
    await expect(page.getByText('EXTERNAL_SERVICE_ERROR')).toBeVisible({ timeout: 5000 });
    await expect(page.getByText(/report alloggiati.*scaricato|report.*downloaded/i)).not.toBeVisible();
    guard.checkpoint('failure surfaced as error toast, no false-positive success');
  });

  test('successful report generation still shows success (no regression on the happy path)', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/stays');
    await page.getByRole('heading', { name: /soggiorni|stays/i }).waitFor();

    await page.locator('#generate-alloggiati-btn').click();
    await expect(page.getByText(/report alloggiati.*scaricato|report.*downloaded/i)).toBeVisible({ timeout: 8000 });
    guard.checkpoint('happy path still shows success');
  });
});
