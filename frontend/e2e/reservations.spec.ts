import { test, expect } from '@playwright/test';

const MOCK_USER = { username: 'admin', role: 'ADMIN', sub: 'admin', mustChangePassword: false };

const MOCK_ROOMS = [
  { id: 'rm-101', roomNumber: '101', status: 'CLEAN', roomType: { id: 'rt-1', name: 'Standard', basePrice: 80 } },
];

const TODAY = new Date().toISOString().split('T')[0];
const TOMORROW = new Date(Date.now() + 86400000).toISOString().split('T')[0];

const MOCK_RESERVATIONS = [
  {
    id: 'res-001',
    guestId: 'g-001',
    guestFullName: 'Mario Rossi',
    checkInDate: TODAY,
    checkOutDate: TOMORROW,
    status: 'CONFIRMED',
    expectedGuests: 2,
    actualGuests: 0,
    active: true,
    lineItems: [{ roomId: 'rm-101', active: true }],
  },
  {
    id: 'res-002',
    guestId: 'g-002',
    guestFullName: 'Anna Bianchi',
    checkInDate: TOMORROW,
    checkOutDate: '2099-01-01',
    status: 'PENDING',
    expectedGuests: 1,
    actualGuests: 0,
    active: true,
    lineItems: [],
  },
];

const CANCELLED_RESERVATION = { ...MOCK_RESERVATIONS[1], status: 'CANCELLED' };

const resPage = {
  content: MOCK_RESERVATIONS,
  totalElements: MOCK_RESERVATIONS.length,
  totalPages: 1,
  number: 0,
  size: 500,
};

const roomsPage = {
  content: MOCK_ROOMS,
  totalElements: MOCK_ROOMS.length,
  totalPages: 1,
  number: 0,
  size: 500,
};

test.describe('Reservations', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_USER) }),
    );
    await page.route('**/api/v1/reservations**', async (route) => {
      const method = route.request().method();
      const url = route.request().url();
      if (method === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(resPage) });
      } else if (method === 'PATCH' && url.includes('status-and-guests') && url.includes('res-002')) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(CANCELLED_RESERVATION) });
      } else if (method === 'PUT' && url.includes('res-002')) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(CANCELLED_RESERVATION) });
      } else if (method === 'POST') {
        await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ ...MOCK_RESERVATIONS[0], id: 'res-new' }) });
      } else {
        await route.fallback();
      }
    });
    await page.route('**/api/v1/rooms**', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(roomsPage) }),
    );
    await page.route('**/api/v1/room-types**', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { id: 'rt-1', name: 'Standard', maxOccupancy: 2, basePrice: 80, description: 'Standard room' },
      ]) }),
    );
    await page.route('**/api/v1/guests**', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        content: [{ id: 'g-001', firstName: 'Mario', lastName: 'Rossi', email: 'mario@test.com' }],
        totalElements: 1, totalPages: 1, number: 0, size: 20,
      }) }),
    );
  });

  test('renders reservation list with both reservations', async ({ page }) => {
    await page.goto('/reservations');
    await expect(page.getByText('Mario Rossi')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Anna Bianchi')).toBeVisible();
  });

  test('shows check-in button for CONFIRMED reservation', async ({ page }) => {
    await page.goto('/reservations');
    await expect(page.getByText('Mario Rossi')).toBeVisible({ timeout: 10000 });
    // Check-in button should appear next to the CONFIRMED reservation
    await expect(page.getByRole('button', { name: /check.in/i }).first()).toBeVisible();
  });

  test('navigates to check-in form when check-in clicked', async ({ page }) => {
    // Also mock stay-service routes used by CheckInForm
    await page.route('**/api/v1/stays/lookup/stati', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }),
    );
    await page.route('**/api/v1/stays/lookup/tipdoc', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }),
    );
    await page.route('**/api/v1/stays/lookup/comuni**', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }),
    );
    await page.route('**/api/v1/stays/settings', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ hotelId: 'h-001', alloggiatiAutoSend: false }) }),
    );
    await page.route('**/api/v1/stays**', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }) }),
    );

    await page.goto('/reservations');
    await expect(page.getByText('Mario Rossi')).toBeVisible({ timeout: 10000 });
    await page.getByRole('button', { name: /check.in/i }).first().click();
    await expect(page).toHaveURL(/\/stays\/check-in\//);
  });

  test('shows cancel button for CONFIRMED and PENDING reservations', async ({ page }) => {
    await page.goto('/reservations');
    await expect(page.getByText('Mario Rossi')).toBeVisible({ timeout: 10000 });
    // Cancel buttons have aria-label="Cancel <reservationId>" (cancel_reservation key → "Cancel")
    const cancelButtons = page.getByRole('button', { name: /^Cancel res-/i });
    await expect(cancelButtons).toHaveCount(2);
  });

  test('cancels reservation and updates status', async ({ page }) => {
    await page.goto('/reservations');
    await expect(page.getByText('Anna Bianchi')).toBeVisible({ timeout: 10000 });
    // Cancel the second reservation (Anna Bianchi, PENDING) — aria-label="Cancel res-002"
    await page.getByRole('button', { name: /Cancel res-002/i }).click();
    // Confirmation dialog opens with a dismiss "Cancel" button and a primary "Confirm" button.
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible({ timeout: 3000 });
    await dialog.getByRole('button', { name: /^Confirm$/ }).click();
    // After cancellation, the CANCELLED chip and/or toast appear — use .first() to avoid strict mode
    await expect(page.getByText(/cancelled/i).first()).toBeVisible({ timeout: 5000 });
  });

  test('navigates to new reservation form', async ({ page }) => {
    await page.goto('/reservations');
    await expect(page.getByText('Mario Rossi')).toBeVisible({ timeout: 10000 });
    await page.getByRole('button', { name: /new reservation|nuova prenotazione/i }).click();
    await expect(page).toHaveURL(/\/reservations\/new/);
  });

  test('client-side search filters by guest name', async ({ page }) => {
    await page.goto('/reservations');
    await expect(page.getByText('Mario Rossi')).toBeVisible({ timeout: 10000 });

    const searchInput = page.getByRole('searchbox');
    await searchInput.fill('anna');

    await expect(page.getByText('Anna Bianchi')).toBeVisible({ timeout: 2000 });
    await expect(page.getByText('Mario Rossi')).not.toBeVisible();
  });
});
