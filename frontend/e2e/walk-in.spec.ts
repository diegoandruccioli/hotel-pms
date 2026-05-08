import { test, expect } from '@playwright/test';

const MOCK_USER = { username: 'admin', role: 'ADMIN', sub: 'admin', mustChangePassword: false };

const MOCK_ROOMS = [
  { id: 'room-001', roomNumber: '101', status: 'AVAILABLE', roomType: { name: 'Standard', basePrice: 80 } },
  { id: 'room-002', roomNumber: '102', status: 'AVAILABLE', roomType: { name: 'Double', basePrice: 120 } },
];

const MOCK_GUEST = {
  id: 'guest-001',
  firstName: 'Lucia',
  lastName: 'Bianchi',
  email: 'lucia.bianchi@test.com',
  active: true,
};

const MOCK_STAY = {
  id: 'stay-walk-001',
  reservationId: null,
  guestId: MOCK_GUEST.id,
  roomId: MOCK_ROOMS[0].id,
  status: 'CHECKED_IN',
  actualCheckInTime: new Date().toISOString(),
  alloggiatiSent: false,
  guests: [],
};

test.describe('Walk-in Check-in', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_USER) }),
    );
    await page.route('**/api/v1/stays', (route) => {
      if (route.request().method() === 'POST') {
        return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(MOCK_STAY) });
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], totalElements: 0, totalPages: 1, number: 0, size: 20 }),
      });
    });
    await page.route('**/api/v1/stays/settings', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ hotelId: 'h-001', alloggiatiAutoSend: false }),
      }),
    );
  });

  test('navigates to walk-in form from stays page', async ({ page }) => {
    await page.goto('/stays');
    await expect(page.getByRole('button', { name: /walk-in/i })).toBeVisible({ timeout: 5000 });
    await page.getByRole('button', { name: /walk-in/i }).click();
    await expect(page).toHaveURL(/\/stays\/walk-in/);
    await expect(page.getByRole('heading', { name: /walk-in/i })).toBeVisible();
  });

  test('shows available rooms in dropdown', async ({ page }) => {
    await page.route('**/api/v1/rooms**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: MOCK_ROOMS,
          totalElements: MOCK_ROOMS.length,
          totalPages: 1,
          number: 0,
          size: 200,
        }),
      }),
    );
    await page.goto('/stays/walk-in');
    const roomSelect = page.locator('#walkin-room');
    await expect(roomSelect).toBeVisible({ timeout: 5000 });
    await expect(roomSelect.locator('option', { hasText: '101' })).toBeAttached();
    await expect(roomSelect.locator('option', { hasText: '102' })).toBeAttached();
  });

  test('shows error when no rooms available', async ({ page }) => {
    await page.route('**/api/v1/rooms**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], totalElements: 0, totalPages: 1, number: 0, size: 200 }),
      }),
    );
    await page.goto('/stays/walk-in');
    await expect(page.getByRole('alert')).toBeVisible({ timeout: 5000 });
  });

  test('blocks submission when room not selected', async ({ page }) => {
    await page.route('**/api/v1/rooms**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: MOCK_ROOMS,
          totalElements: MOCK_ROOMS.length,
          totalPages: 1,
          number: 0,
          size: 200,
        }),
      }),
    );
    await page.goto('/stays/walk-in');
    await page.getByRole('button', { name: /complete walk-in/i }).click();
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page).toHaveURL(/\/stays\/walk-in/);
  });

  test('completes walk-in and redirects to stays', async ({ page }) => {
    await page.route('**/api/v1/rooms**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: MOCK_ROOMS,
          totalElements: MOCK_ROOMS.length,
          totalPages: 1,
          number: 0,
          size: 200,
        }),
      }),
    );
    await page.route('**/api/v1/guests**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [MOCK_GUEST],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 20,
        }),
      }),
    );

    await page.goto('/stays/walk-in');

    // Select room
    await page.locator('#walkin-room').selectOption({ value: 'room-001' });

    // Search and select guest
    await page.locator('#walkin-guest').fill('Lucia');
    await page.getByRole('option', { name: /Lucia Bianchi/i }).click();

    // Set checkout date (tomorrow)
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const iso = tomorrow.toISOString().split('T')[0];
    await page.locator('#walkin-checkout').fill(iso);

    // Submit
    await page.getByRole('button', { name: /complete walk-in/i }).click();
    await expect(page).toHaveURL(/\/stays/, { timeout: 5000 });
  });
});
