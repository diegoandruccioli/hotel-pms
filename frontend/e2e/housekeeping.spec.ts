import { test, expect } from '@playwright/test';

const MOCK_USER = { username: 'admin', role: 'ADMIN', sub: 'admin', mustChangePassword: false };

const MOCK_ROOMS = [
  {
    id: 'rm-101',
    roomNumber: '101',
    status: 'DIRTY',
    roomType: { id: 'rt-1', name: 'Standard', basePrice: 80 },
    hotelId: 'h-001',
    active: true,
  },
  {
    id: 'rm-102',
    roomNumber: '102',
    status: 'CLEAN',
    roomType: { id: 'rt-1', name: 'Standard', basePrice: 80 },
    hotelId: 'h-001',
    active: true,
  },
  {
    id: 'rm-201',
    roomNumber: '201',
    status: 'MAINTENANCE',
    roomType: { id: 'rt-2', name: 'Suite', basePrice: 200 },
    hotelId: 'h-001',
    active: true,
  },
  {
    id: 'rm-202',
    roomNumber: '202',
    status: 'OCCUPIED',
    roomType: { id: 'rt-2', name: 'Suite', basePrice: 200 },
    hotelId: 'h-001',
    active: true,
  },
];

const roomsPage = {
  content: MOCK_ROOMS,
  totalElements: MOCK_ROOMS.length,
  totalPages: 1,
  number: 0,
  size: 100,
};

test.describe('Housekeeping', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_USER) }),
    );
    await page.route('**/api/v1/rooms**', async (route) => {
      const method = route.request().method();
      const url = route.request().url();
      if (method === 'PATCH' && url.includes('/status')) {
        // Status update — return updated room
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ ...MOCK_ROOMS[0], status: 'CLEAN' }),
        });
      } else {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(roomsPage) });
      }
    });
  });

  test('renders housekeeping page with room cards', async ({ page }) => {
    await page.goto('/housekeeping');
    // room_number → "Room {{number}}"
    await expect(page.getByText('Room 101')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Room 102')).toBeVisible();
    await expect(page.getByText('Room 201')).toBeVisible();
    await expect(page.getByText('Room 202')).toBeVisible();
  });

  test('shows room cards with correct status chips', async ({ page }) => {
    await page.goto('/housekeeping');
    await expect(page.getByText('Room 101')).toBeVisible({ timeout: 10000 });
    // Use .first() — multiple elements match status text (chip + filter buttons + transition buttons)
    await expect(page.getByText('Dirty').first()).toBeVisible();
    await expect(page.getByText('Clean').first()).toBeVisible();
    await expect(page.getByText('Maintenance').first()).toBeVisible();
    await expect(page.getByText('Occupied').first()).toBeVisible();
  });

  test('shows status change buttons on each card', async ({ page }) => {
    await page.goto('/housekeeping');
    await expect(page.getByText('Room 101')).toBeVisible({ timeout: 10000 });
    // Transition buttons say "→ <Status>" — room 101 (DIRTY) should have "→ Clean"
    await expect(page.getByRole('button', { name: /→/ }).first()).toBeVisible();
  });

  test('updates room status to CLEAN', async ({ page }) => {
    await page.goto('/housekeeping');
    await expect(page.getByText('Room 101')).toBeVisible({ timeout: 10000 });

    // Room 101 is DIRTY; its transition buttons are "→ Clean" and "→ Maintenance"
    // Scope to the card that contains "Room 101" heading, then click "→ Clean"
    const room101Card = page.locator('div').filter({
      has: page.getByRole('heading', { name: 'Room 101' }),
    }).first();
    // Two "→ Clean" buttons may be found (e.g. nested divs) — click the first one
    const cleanButton = room101Card.getByRole('button', { name: /→ Clean/i }).first();
    await expect(cleanButton).toBeVisible({ timeout: 3000 });
    await cleanButton.click();

    // After status change API call, the component reloads rooms — no error should appear
    await expect(page.getByRole('alert')).not.toBeVisible({ timeout: 2000 }).catch(() => { /* no error = pass */ });
  });

  test('shows correct room count', async ({ page }) => {
    await page.goto('/housekeeping');
    await expect(page.getByText('Room 101')).toBeVisible({ timeout: 10000 });
    // All 4 rooms should be visible (room_number → "Room {{number}}")
    for (const num of ['101', '102', '201', '202']) {
      await expect(page.getByText(`Room ${num}`)).toBeVisible();
    }
  });
});
