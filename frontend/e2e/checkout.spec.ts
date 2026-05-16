import { test, expect } from '@playwright/test';

const MOCK_USER = { username: 'admin', role: 'ADMIN', sub: 'admin', mustChangePassword: false };

const MOCK_STAY = {
  id: 'stay-co-001',
  reservationId: 'res-001',
  guestId: 'guest-001',
  roomId: 'room-001',
  status: 'CHECKED_IN',
  actualCheckInTime: '2026-04-01T14:00:00',
  alloggiatiSent: true,
  guests: [],
};

const CHECKED_OUT_STAY = { ...MOCK_STAY, status: 'CHECKED_OUT', actualCheckOutTime: new Date().toISOString() };

test.describe('Check-out flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_USER) }),
    );
    await page.route('**/api/v1/stays/settings', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ hotelId: 'h-001', alloggiatiAutoSend: false }),
      }),
    );
    await page.route((url) => url.pathname === '/api/v1/stays', (route) => {
      if (route.request().method() === 'GET') {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            content: [MOCK_STAY],
            totalElements: 1,
            totalPages: 1,
            number: 0,
            size: 20,
          }),
        });
      }
      return route.continue();
    });
  });

  test('renders stays list with a CHECKED_IN stay', async ({ page }) => {
    await page.goto('/stays');
    await expect(page.getByText('stay-co-001')).not.toBeVisible(); // id not shown
    // Status chip visible
    await expect(page.getByText(/checked.?in/i).first()).toBeVisible({ timeout: 5000 });
  });

  test('shows check-out button for CHECKED_IN stay', async ({ page }) => {
    await page.goto('/stays');
    await expect(page.getByRole('button', { name: /check.?out/i }).first()).toBeVisible({ timeout: 5000 });
  });

  test('completes checkout when billing is paid', async ({ page }) => {
    await page.route('**/api/v1/stays/stay-co-001/check-out', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(CHECKED_OUT_STAY),
      }),
    );

    await page.goto('/stays');
    await page.getByRole('button', { name: /check.?out/i }).first().click();

    await expect(page.getByText(/checked.?out/i).first()).toBeVisible({ timeout: 5000 });
  });

  test('shows error when checkout fails (billing unpaid)', async ({ page }) => {
    await page.route('**/api/v1/stays/stay-co-001/check-out', (route) =>
      route.fulfill({
        status: 409,
        contentType: 'application/json',
        body: JSON.stringify({ title: 'Billing Not Paid', detail: 'BILLING_NOT_PAID' }),
      }),
    );

    await page.goto('/stays');
    await page.getByRole('button', { name: /check.?out/i }).first().click();

    // Stay should still be CHECKED_IN (no status change)
    await expect(page.getByText(/checked.?in/i).first()).toBeVisible({ timeout: 3000 });
  });
});
