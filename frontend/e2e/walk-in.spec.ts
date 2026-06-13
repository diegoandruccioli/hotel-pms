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

// Alloggiati lookup data — must be present so the LookupAutocomplete can
// filter results when the user types in the stato-nascita field.
const MOCK_STATI = [
  { codice: 'EE', descrizione: 'ESTERO' },
  { codice: '100000100', descrizione: 'ITALIA' },
];

test.describe('Walk-in Check-in', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_USER) }),
    );
    // Use pathname-based matcher so query params (?page=0&size=20&sort=...) are ignored.
    await page.route((url) => url.pathname === '/api/v1/stays', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(MOCK_STAY) });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ content: [], totalElements: 0, totalPages: 1, number: 0, size: 20 }),
        });
      }
    });
    await page.route('**/api/v1/stays/settings', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ hotelId: 'h-001', alloggiatiAutoSend: false }),
      }),
    );
    // Default empty rooms so /stays/walk-in doesn't trigger 401 → logout redirect.
    // Individual tests that need specific rooms data override this with their own mock.
    await page.route('**/api/v1/rooms**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], totalElements: 0, totalPages: 1, number: 0, size: 200 }),
      }),
    );
    // Lookup endpoints must be mocked to prevent 401 responses from the running
    // backend triggering the Axios interceptor → performLogout() → /login redirect.
    await page.route('**/api/v1/stays/lookup/stati', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_STATI) }),
    );
    await page.route('**/api/v1/stays/lookup/tipdoc', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { codice: 'PP', descrizione: 'PASSAPORTO' },
      ]) }),
    );
    await page.route('**/api/v1/stays/lookup/comuni**', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }),
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
    await expect(roomSelect).toBeVisible({ timeout: 10000 });
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
    // Wait for rooms to load (select appears only when rooms.length > 0)
    await expect(page.locator('#walkin-room')).toBeVisible({ timeout: 10000 });
    // Click submit without selecting a room — should show validation error
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
    // Wait for rooms to load before interacting
    await expect(page.locator('#walkin-room')).toBeVisible({ timeout: 10000 });

    // Select room
    await page.locator('#walkin-room').selectOption({ value: 'room-001' });

    // Search and select guest
    await page.locator('#walkin-guest').fill('Lucia');
    await expect(page.getByRole('option', { name: /Lucia Bianchi/i })).toBeVisible({ timeout: 3000 });
    await page.getByRole('option', { name: /Lucia Bianchi/i }).click();

    // Set checkout date (tomorrow)
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const iso = tomorrow.toISOString().split('T')[0];
    await page.locator('#walkin-checkout').fill(iso);

    // Fill mandatory Alloggiati fields (added in F2 sprint).
    // Choose FAMILIARE type (no document required) and a foreign birthplace
    // (no Italian comune required) — minimum path through frontend validation.
    await page.locator('#traveller-type-0').selectOption({ value: 'FAMILIARE' });

    // Type at least 2 chars to trigger the autocomplete, then click the option.
    await page.locator('#stato-nascita-0').fill('ES');
    await expect(page.getByRole('option', { name: /ESTERO/i })).toBeVisible({ timeout: 3000 });
    await page.getByRole('option', { name: /ESTERO/i }).click();

    // Submit
    await page.getByRole('button', { name: /complete walk-in/i }).click();
    await expect(page).toHaveURL(/\/stays/, { timeout: 5000 });
  });
});
