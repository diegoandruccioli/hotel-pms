import { test, expect } from '@playwright/test';

const MOCK_USER = { username: 'admin', role: 'ADMIN', sub: 'admin', mustChangePassword: false };

const MOCK_ROOM_TYPES = [
  { id: 'rt-1', name: 'Standard', maxOccupancy: 2, basePrice: 80.00, description: 'Standard double room' },
  { id: 'rt-2', name: 'Suite', maxOccupancy: 4, basePrice: 200.00, description: 'Luxury suite' },
];

const MOCK_ROOMS = [
  {
    id: 'rm-101',
    roomNumber: '101',
    status: 'CLEAN',
    roomType: MOCK_ROOM_TYPES[0],
    hotelId: 'h-001',
    active: true,
  },
  {
    id: 'rm-201',
    roomNumber: '201',
    status: 'DIRTY',
    roomType: MOCK_ROOM_TYPES[1],
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

const NEW_ROOM_TYPE = { id: 'rt-3', name: 'Deluxe', maxOccupancy: 2, basePrice: 150.00, description: 'Deluxe room' };
const NEW_ROOM = { id: 'rm-301', roomNumber: '301', status: 'CLEAN', roomType: MOCK_ROOM_TYPES[0], hotelId: 'h-001', active: true };

test.describe('Rooms management', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_USER) }),
    );
    await page.route('**/api/v1/rooms**', async (route) => {
      const method = route.request().method();
      if (method === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(roomsPage) });
      } else if (method === 'POST') {
        await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(NEW_ROOM) });
      } else {
        await route.fallback();
      }
    });
    await page.route('**/api/v1/room-types', async (route) => {
      const method = route.request().method();
      if (method === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_ROOM_TYPES) });
      } else if (method === 'POST') {
        await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(NEW_ROOM_TYPE) });
      } else {
        await route.fallback();
      }
    });
    await page.route('**/api/v1/room-types/**', async (route) => {
      const method = route.request().method();
      if (method === 'PUT') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_ROOM_TYPES[0]) });
      } else {
        await route.fallback();
      }
    });
  });

  test('renders rooms page with tab navigation', async ({ page }) => {
    await page.goto('/rooms');
    // rooms_title → "Inventory" (h1); also h2="Physical Rooms" exists — scope to level 1
    await expect(page.getByRole('heading', { name: /inventory/i, level: 1 })).toBeVisible({ timeout: 10000 });
    // tab_rooms → "Physical Rooms", tab_room_types → "Room Categories"
    await expect(page.getByRole('button', { name: /Physical Rooms/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /Room Categories/i })).toBeVisible();
  });

  test('shows room list on Rooms tab', async ({ page }) => {
    await page.goto('/rooms');
    await expect(page.getByText('101')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('201')).toBeVisible();
  });

  test('switches to room types tab and shows types', async ({ page }) => {
    await page.goto('/rooms');
    await page.getByRole('button', { name: /Room Categories/i }).click();
    await expect(page.getByRole('cell', { name: 'Standard', exact: true })).toBeVisible({ timeout: 5000 });
    await expect(page.getByRole('cell', { name: 'Suite', exact: true })).toBeVisible();
    await expect(page.getByText('80.00')).toBeVisible();
  });

  test('opens add room type modal', async ({ page }) => {
    await page.goto('/rooms');
    await page.getByRole('button', { name: /Room Categories/i }).click();
    await expect(page.getByRole('cell', { name: 'Standard', exact: true })).toBeVisible({ timeout: 5000 });
    // add_room_type → "Add Category"
    await page.getByRole('button', { name: /Add Category/i }).click();
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: 3000 });
  });

  test('creates a new room type', async ({ page }) => {
    await page.goto('/rooms');
    await page.getByRole('button', { name: /Room Categories/i }).click();
    await expect(page.getByRole('cell', { name: 'Standard', exact: true })).toBeVisible({ timeout: 5000 });

    await page.getByRole('button', { name: /Add Category/i }).click();
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: 3000 });

    await page.getByRole('textbox', { name: /name|nome/i }).fill('Deluxe');
    await page.getByRole('spinbutton', { name: /occupancy|ospiti/i }).fill('2');
    await page.getByRole('spinbutton', { name: /price|prezzo/i }).fill('150');

    await page.getByRole('button', { name: /save|salva/i }).click();
    await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 5000 });
  });

  test('opens add room modal from Rooms tab', async ({ page }) => {
    await page.goto('/rooms');
    await expect(page.getByText('101')).toBeVisible({ timeout: 10000 });
    await page.getByRole('button', { name: /add room|aggiungi camera/i }).click();
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: 3000 });
  });

  test('shows room status chips with correct statuses', async ({ page }) => {
    await page.goto('/rooms');
    await expect(page.getByText('101')).toBeVisible({ timeout: 10000 });
    // room_status_clean → "Clean", room_status_dirty → "Dirty"
    // Use .first() — nav may contain material icon text "cleaning_services" that also matches /clean/i
    await expect(page.getByText(/^Clean$/).first()).toBeVisible();
    await expect(page.getByText(/^Dirty$/).first()).toBeVisible();
  });
});
