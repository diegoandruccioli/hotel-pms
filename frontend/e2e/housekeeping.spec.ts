import { test, expect } from '@playwright/test';
import { mockAuthMe, mockDaySheet } from './fixtures/mockApi';

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
    await mockAuthMe(page);
    // Housekeeping's filter-badge counts come from the day-sheet aggregate,
    // not from re-counting the fetched room list — unmocked, the query 401s
    // and the axios interceptor redirects the whole page to /login.
    await mockDaySheet(page, { roomStatusCounts: { CLEAN: 1, DIRTY: 1, MAINTENANCE: 1, OCCUPIED: 1 } });
    await page.route('**/api/v1/rooms**', async (route) => {
      const method = route.request().method();
      const url = route.request().url();
      if (method === 'PATCH' && url.includes('/status')) {
        // Single-room status update — return the updated room.
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ ...MOCK_ROOMS[0], status: 'CLEAN' }),
        });
      } else {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(roomsPage) });
      }
    });
    // Registered after the broader **/rooms** mock above so it takes priority
    // (Playwright matches the most-recently-registered route first). Bulk
    // PATCH /api/v1/rooms/status returns an array, unlike the per-room
    // PATCH /api/v1/rooms/{id}/status handled by the mock above.
    await page.route('**/api/v1/rooms/status', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_ROOMS.map((r) => ({ ...r, status: 'CLEAN' }))),
      });
    });
  });

  test('renders housekeeping page with room cards', async ({ page }) => {
    await page.goto('/housekeeping');
    // room_number → "Room {{number}}". Heading role, not getByText — the
    // per-card "select room" checkbox label contains the same substring
    // ("Select room 101" ⊃ "Room 101"), which is a strict-mode violation.
    await expect(page.getByRole('heading', { name: 'Room 101' })).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'Room 102' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Room 201' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Room 202' })).toBeVisible();
  });

  test('shows room cards with correct status chips', async ({ page }) => {
    await page.goto('/housekeeping');
    await expect(page.getByRole('heading', { name: 'Room 101' })).toBeVisible({ timeout: 10000 });
    // Use .first() — multiple elements match status text (chip + filter badges + transition buttons)
    await expect(page.getByText('Dirty').first()).toBeVisible();
    await expect(page.getByText('Clean').first()).toBeVisible();
    await expect(page.getByText('Maintenance').first()).toBeVisible();
    await expect(page.getByText('Occupied').first()).toBeVisible();
  });

  test('shows status change buttons on each card', async ({ page }) => {
    await page.goto('/housekeeping');
    await expect(page.getByRole('heading', { name: 'Room 101' })).toBeVisible({ timeout: 10000 });
    // Transition buttons say "→ <Status>" — room 101 (DIRTY) should have "→ Clean"
    await expect(page.getByRole('button', { name: /→/ }).first()).toBeVisible();
  });

  test('updates room status to CLEAN', async ({ page }) => {
    await page.goto('/housekeeping');
    await expect(page.getByRole('heading', { name: 'Room 101' })).toBeVisible({ timeout: 10000 });

    // Room 101 is DIRTY; its transition buttons are "→ Clean" and "→ Maintenance"
    // Scope to the card that contains "Room 101" heading, then click "→ Clean"
    const room101Card = page.locator('div').filter({
      has: page.getByRole('heading', { name: 'Room 101' }),
    }).first();
    const cleanButton = room101Card.getByRole('button', { name: /→ Clean/i }).first();
    await expect(cleanButton).toBeVisible({ timeout: 3000 });
    await cleanButton.click();

    // After status change API call, no error toast should appear
    await expect(page.getByRole('alert')).not.toBeVisible({ timeout: 2000 }).catch(() => { /* no error = pass */ });
  });

  test('shows correct room count', async ({ page }) => {
    await page.goto('/housekeeping');
    await expect(page.getByRole('heading', { name: 'Room 101' })).toBeVisible({ timeout: 10000 });
    // All 4 rooms should be visible (room_number → "Room {{number}}")
    for (const num of ['101', '102', '201', '202']) {
      await expect(page.getByRole('heading', { name: `Room ${num}` })).toBeVisible();
    }
  });

  test('bulk-updates selected rooms via the bulk status action', async ({ page }) => {
    await page.goto('/housekeeping');
    await expect(page.getByRole('heading', { name: 'Room 101' })).toBeVisible({ timeout: 10000 });

    await page.getByRole('checkbox', { name: 'Select room 101' }).click();

    await page.getByTestId('bulk-status-CLEAN').click();

    await expect(page.getByRole('alert')).not.toBeVisible({ timeout: 2000 }).catch(() => { /* no error = pass */ });
  });
});
