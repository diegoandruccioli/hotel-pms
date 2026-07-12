import { test, expect } from '@playwright/test';

const MOCK_USER = { username: 'admin', role: 'ADMIN', sub: 'admin', mustChangePassword: false };

const MOCK_INVOICE = {
  id: 'inv-001',
  hotelId: 'h-001',
  invoiceNumber: '2026/0001',
  issueDate: '2026-04-01T10:00:00',
  totalAmount: 320.00,
  status: 'ISSUED',
  reservationId: 'res-001',
  guestId: 'guest-001',
  stayId: 'stay-001',
  documentType: 'FATTURA',
  sdiStatus: 'NOT_SENT',
  payments: [],
  charges: [],
};

test.describe('Billing flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_USER) }),
    );
    // '**/api/v1/invoices**' matches both /api/v1/invoices and /api/v1/invoices?page=0&size=20
    await page.route('**/api/v1/invoices**', (route) => {
      if (route.request().url().includes('/api/v1/invoices/inv-001')) {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(MOCK_INVOICE),
        });
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [MOCK_INVOICE],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 20,
        }),
      });
    });
    await page.route('**/api/v1/invoices/inv-001', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_INVOICE),
      }),
    );
  });

  test('renders billing list with an invoice', async ({ page }) => {
    await page.goto('/billing');
    await expect(page.locator('table')).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('2026/0001')).toBeVisible();
  });

  test('invoice detail modal shows SDI section for FATTURA', async ({ page }) => {
    await page.route('**/api/v1/invoices/inv-001/fatturaPA', (route) =>
      route.fulfill({ status: 200, contentType: 'application/xml', body: '<FatturaElettronica/>' }),
    );
    await page.goto('/billing');
    await expect(page.locator('table')).toBeVisible({ timeout: 5000 });
    await page.getByRole('button', { name: /View/i }).first().click();
    await expect(page.getByText('SDI Status')).toBeVisible({ timeout: 3000 });
    await expect(page.getByRole('button', { name: /Download FatturaPA XML/i })).toBeVisible();
  });

  test('passes accessibility audit on billing page', async ({ page }) => {
    const AxeBuilder = (await import('@axe-core/playwright')).default;
    await page.goto('/billing');
    await page.waitForLoadState('networkidle');
    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations.filter(v => v.impact === 'critical')).toHaveLength(0);
  });
});
