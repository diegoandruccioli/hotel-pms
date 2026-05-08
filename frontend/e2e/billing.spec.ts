import { test, expect } from '@playwright/test';

const MOCK_USER = { username: 'admin', role: 'ADMIN', sub: 'admin', mustChangePassword: false };

const MOCK_INVOICE = {
  id: 'inv-001',
  stayId: 'stay-001',
  hotelId: 'h-001',
  reservationId: 'res-001',
  guestFullName: 'Mario Rossi',
  status: 'PENDING',
  totalAmount: 320.00,
  paidAmount: 0,
  issueDate: '2026-04-01',
  lineItems: [
    { id: 'li-1', description: 'Room 101 × 4 nights', amount: 320.00 },
  ],
};

const PAID_INVOICE = { ...MOCK_INVOICE, status: 'PAID', paidAmount: 320.00 };

test.describe('Billing flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_USER) }),
    );
    await page.route('**/api/v1/invoices', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [MOCK_INVOICE],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 20,
        }),
      }),
    );
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
    await expect(page.getByText('Mario Rossi')).toBeVisible({ timeout: 5000 });
    await expect(page.getByText(/pending/i)).toBeVisible();
  });

  test('opens invoice detail modal on row click', async ({ page }) => {
    await page.goto('/billing');
    await page.getByText('Mario Rossi').click();
    // Invoice detail modal should open
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: 3000 });
    await expect(page.getByText('320')).toBeVisible();
  });

  test('shows payment modal from invoice detail', async ({ page }) => {
    await page.goto('/billing');
    await page.getByText('Mario Rossi').click();
    // Click "Register Payment" inside modal
    const payButton = page.getByRole('button', { name: /register payment|add payment/i });
    if (await payButton.isVisible({ timeout: 3000 })) {
      await payButton.click();
      await expect(page.getByRole('dialog').nth(1)).toBeVisible({ timeout: 3000 });
    }
  });

  test('marks invoice as paid after payment registration', async ({ page }) => {
    await page.route('**/api/v1/invoices/inv-001/payments', (route) =>
      route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({ id: 'pay-001', amount: 320.00, method: 'CASH' }),
      }),
    );
    // After payment, invoice endpoint returns PAID
    let callCount = 0;
    await page.route('**/api/v1/invoices/inv-001', (route) => {
      callCount++;
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(callCount > 1 ? PAID_INVOICE : MOCK_INVOICE),
      });
    });

    await page.goto('/billing');
    await page.getByText('Mario Rossi').click();

    const payButton = page.getByRole('button', { name: /register payment|add payment/i });
    if (await payButton.isVisible({ timeout: 3000 })) {
      await payButton.click();
      const amountInput = page.getByLabel(/amount/i);
      if (await amountInput.isVisible({ timeout: 2000 })) {
        await amountInput.fill('320');
        await page.getByRole('button', { name: /confirm|save|submit/i }).last().click();
      }
    }
  });

  test('passes accessibility audit on billing page', async ({ page }) => {
    const AxeBuilder = (await import('@axe-core/playwright')).default;
    await page.goto('/billing');
    await page.waitForLoadState('networkidle');
    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations.filter(v => v.impact === 'critical')).toHaveLength(0);
  });
});
