import { test, expect } from '@playwright/test';

const MOCK_USER = { username: 'admin', role: 'ADMIN', sub: 'admin', mustChangePassword: false };

const MOCK_ROOMS = [
  { id: 'rm-1', roomNumber: '101', status: 'CLEAN', roomType: { name: 'Standard', basePrice: 80 } },
  { id: 'rm-2', roomNumber: '102', status: 'DIRTY', roomType: { name: 'Double', basePrice: 120 } },
  { id: 'rm-3', roomNumber: '103', status: 'OCCUPIED', roomType: { name: 'Suite', basePrice: 200 } },
];

const TODAY = new Date().toISOString().split('T')[0];

const MOCK_GUESTS = [
  { id: 'g-1', firstName: 'Mario', lastName: 'Rossi', email: 'mario@test.com' },
  { id: 'g-2', firstName: 'Anna', lastName: 'Bianchi', email: 'anna@test.com' },
];

const MOCK_RESERVATIONS = [
  {
    id: 'res-1', guestId: 'g-1', guestFullName: 'Mario Rossi',
    checkInDate: TODAY, checkOutDate: '2099-01-01',
    status: 'CONFIRMED', expectedGuests: 1, active: true, lineItems: [],
  },
];

const MOCK_STAYS = [
  { id: 'stay-1', guestId: 'g-1', roomId: 'rm-2', status: 'CHECKED_IN' },
];

const MOCK_REPORT = {
  invoices: [
    { id: 'inv-1', status: 'ISSUED', totalAmount: 150.0 },
    { id: 'inv-2', status: 'PAID', totalAmount: 200.0 },
  ],
};

async function mockDashboardApis(page: import('@playwright/test').Page): Promise<void> {
  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_USER) }),
  );
  await page.route('**/api/v1/guests**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: MOCK_GUESTS, totalElements: 2, totalPages: 1, number: 0, size: 20 }),
    }),
  );
  await page.route('**/api/v1/reservations**', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: MOCK_RESERVATIONS, totalElements: 1, totalPages: 1, number: 0, size: 500 }),
      });
    } else {
      await route.fallback();
    }
  });
  // Use pathname-based matcher so query params are not a concern.
  await page.route((url) => url.pathname === '/api/v1/stays', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: MOCK_STAYS, totalElements: 1, totalPages: 1, number: 0, size: 20 }),
    }),
  );
  await page.route('**/api/v1/stays/settings', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ hotelId: 'h-001', alloggiatiAutoSend: false }) }),
  );
  await page.route('**/api/v1/rooms**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: MOCK_ROOMS, totalElements: MOCK_ROOMS.length, totalPages: 1, number: 0, size: 100 }),
    }),
  );
  await page.route('**/api/v1/reports/owner**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_REPORT) }),
  );
  // Dashboard fires this in a separate effect (admin/owner only) — unmocked, it 401s against
  // the real backend and the global axios interceptor's silent-refresh-then-logout kicks in,
  // hard-redirecting to /login mid-test (T-DASH-E2E flake root cause, fixed 2026-06-22).
  await page.route('**/api/v1/stays/reports/alloggiati/failures/summary', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ failedCount: 0 }) }),
  );
}

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await mockDashboardApis(page);
  });

  test('renders dashboard heading with username', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('dashboard-heading')).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId('dashboard-heading')).toContainText('admin');
  });

  test('renders stat cards grid', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('stats-grid')).toBeVisible({ timeout: 10000 });
    // All stat card labels should be present (verify by translation keys rendered as text)
    await expect(page.getByText(/total guests|ospiti totali/i)).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/today.*(arrivals|check.in)|arrivi/i)).toBeVisible();
  });

  test('shows total guests count after stats load', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('stats-grid')).toBeVisible({ timeout: 10000 });
    // Find the dl card labelled "Total Guests" and assert the dd shows "2"
    // Uses dt/dd structure from Dashboard component
    const guestsDl = page.locator('dl').filter({ hasText: /Total Guests/i });
    await expect(guestsDl.locator('dd')).toContainText('2', { timeout: 10000 });
  });

  test('renders room overview grid when rooms present', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('room-overview-grid')).toBeVisible({ timeout: 10000 });
    // All 3 rooms should appear as cells
    await expect(page.getByTestId('room-overview-grid').getByText('101')).toBeVisible();
    await expect(page.getByTestId('room-overview-grid').getByText('102')).toBeVisible();
    await expect(page.getByTestId('room-overview-grid').getByText('103')).toBeVisible();
  });

  test('shows today arrivals — 1 CONFIRMED reservation with checkInDate=today', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('stats-grid')).toBeVisible({ timeout: 10000 });
    // stat_today_checkins → "Today's Check-ins"; dl/dd structure
    const arrivalsDl = page.locator('dl').filter({ hasText: /Today.*Check-in/i });
    await expect(arrivalsDl.locator('dd')).toContainText('1', { timeout: 10000 });
  });

  test('pending revenue card visible for ADMIN role', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('stats-grid')).toBeVisible({ timeout: 10000 });
    // ISSUED invoice totalAmount = 150 → pending revenue card shows "€150.00" or "150"
    await expect(page.getByText(/150/)).toBeVisible({ timeout: 10000 });
  });

  test('stat cards link to correct pages', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('stats-grid')).toBeVisible({ timeout: 10000 });
    // Each stat card has a "View all" link; room overview section also has one
    // Admin: 4 universal stats + 1 owner stat + 1 room overview = 6 total
    const viewAllLinks = page.getByRole('link', { name: /view all/i });
    await expect(viewAllLinks.first()).toBeVisible({ timeout: 5000 });
    await expect(viewAllLinks).toHaveCount(6);
  });
});
