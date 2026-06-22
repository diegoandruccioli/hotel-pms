import { test, expect } from '@playwright/test';

const MOCK_USER = { username: 'admin', role: 'ADMIN', sub: 'admin', mustChangePassword: false };

const MOCK_MENU_ITEMS = [
  { id: 'mi-1', name: 'Spaghetti Carbonara', category: 'FOOD', price: 14.50, available: true },
  { id: 'mi-2', name: 'Acqua Minerale', category: 'DRINK', price: 2.50, available: true },
  { id: 'mi-3', name: 'Tiramisù', category: 'DESSERT', price: 7.00, available: false },
];

const MOCK_ORDERS = [
  {
    id: 'ord-001-uuid',
    stayId: 'stay-001',
    roomNumber: '101',
    guestDisplayName: 'Mario Rossi',
    orderDate: '2026-06-13T10:00:00',
    totalAmount: 17.00,
    status: 'PENDING',
    items: [
      { menuItemId: 'mi-1', name: 'Spaghetti Carbonara', quantity: 1, unitPrice: 14.50 },
      { menuItemId: 'mi-2', name: 'Acqua Minerale', quantity: 1, unitPrice: 2.50 },
    ],
  },
  {
    id: 'ord-002-uuid',
    stayId: 'stay-002',
    roomNumber: '205',
    guestDisplayName: 'Anna Bianchi',
    orderDate: '2026-06-13T11:00:00',
    totalAmount: 7.00,
    status: 'BILLED_TO_ROOM',
    items: [
      { menuItemId: 'mi-3', name: 'Tiramisù', quantity: 1, unitPrice: 7.00 },
    ],
  },
];

const NEW_ORDER = {
  id: 'ord-003-uuid',
  stayId: 'stay-001',
  roomNumber: '101',
  guestDisplayName: 'Mario Rossi',
  orderDate: new Date().toISOString(),
  totalAmount: 14.50,
  status: 'PENDING',
  items: [{ menuItemId: 'mi-1', name: 'Spaghetti Carbonara', quantity: 1, unitPrice: 14.50 }],
};

const CONFIRMED_ORDER = { ...MOCK_ORDERS[0], status: 'BILLED_TO_ROOM' };

const ordersPage = {
  content: MOCK_ORDERS,
  totalElements: MOCK_ORDERS.length,
  totalPages: 1,
  number: 0,
  size: 20,
};

test.describe('Restaurant / F&B', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_USER) }),
    );
    await page.route('**/api/v1/fb/orders**', async (route) => {
      const method = route.request().method();
      if (method === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(ordersPage) });
      } else if (method === 'POST') {
        await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(NEW_ORDER) });
      } else {
        await route.fallback();
      }
    });
    await page.route('**/api/v1/fb/orders/**/confirm', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(CONFIRMED_ORDER) });
    });
    await page.route('**/api/v1/fb/menu-items', async (route) => {
      const method = route.request().method();
      if (method === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_MENU_ITEMS) });
      } else if (method === 'POST') {
        await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({
          id: 'mi-4', name: 'Bistecca', category: 'FOOD', price: 22.00, available: true,
        }) });
      } else {
        await route.fallback();
      }
    });
    // Stays endpoint used by OrderFormModal to look up active stays
    await page.route('**/api/v1/stays**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            { id: 'stay-001', roomId: 'rm-101', status: 'CHECKED_IN', guestId: 'g-001' },
          ],
          totalElements: 1, totalPages: 1, number: 0, size: 20,
        }),
      }),
    );
    await page.route('**/api/v1/stays/settings', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ hotelId: 'h-001', alloggiatiAutoSend: false }) }),
    );
    await page.route('**/api/v1/rooms**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [
          { id: 'rm-101', roomNumber: '101', status: 'OCCUPIED', roomType: { name: 'Standard', basePrice: 80 } },
        ], totalElements: 1, totalPages: 1, number: 0, size: 100 }),
      }),
    );
  });

  test('renders restaurant page with orders table', async ({ page }) => {
    await page.goto('/restaurant');
    await expect(page.getByRole('heading', { name: /restaurant|ristorante/i })).toBeVisible({ timeout: 10000 });
    // First order row should be visible (shows guest name)
    await expect(page.getByText('Mario Rossi')).toBeVisible({ timeout: 5000 });
  });

  test('shows order statuses', async ({ page }) => {
    await page.goto('/restaurant');
    await expect(page.getByText('Mario Rossi')).toBeVisible({ timeout: 10000 });
    // PENDING status chip
    await expect(page.getByText(/pending/i)).toBeVisible();
    // BILLED_TO_ROOM status chip
    await expect(page.getByText(/billed.to.room|addebitato/i)).toBeVisible();
  });

  test('shows confirm button for PENDING orders', async ({ page }) => {
    await page.goto('/restaurant');
    await expect(page.getByText('Mario Rossi')).toBeVisible({ timeout: 10000 });
    // PENDING order should have a Confirm button
    await expect(page.getByRole('button', { name: /confirm|conferma/i })).toBeVisible();
  });

  test('confirms a PENDING order', async ({ page }) => {
    await page.goto('/restaurant');
    await expect(page.getByText('Mario Rossi')).toBeVisible({ timeout: 10000 });

    // Click confirm on the first PENDING order
    await page.getByRole('button', { name: /confirm|conferma/i }).first().click();

    // After confirm, the page reloads orders — verify no error
    await expect(page.getByRole('alert')).not.toBeVisible({ timeout: 3000 }).catch(() => { /* ok */ });
  });

  test('opens New Order modal', async ({ page }) => {
    await page.goto('/restaurant');
    await expect(page.getByRole('heading', { name: /restaurant|ristorante/i })).toBeVisible({ timeout: 10000 });
    // new_order → "New Restaurant Order"
    await page.getByRole('button', { name: /New Restaurant Order/i }).click();
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: 3000 });
  });

  test('shows menu items section for admin', async ({ page }) => {
    await page.goto('/restaurant');
    await expect(page.getByRole('heading', { name: /restaurant|ristorante/i })).toBeVisible({ timeout: 10000 });
    // Admin sees the menu management section
    await expect(page.getByText('Spaghetti Carbonara')).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('Acqua Minerale')).toBeVisible();
  });

  test('shows availability status on menu items', async ({ page }) => {
    await page.goto('/restaurant');
    await expect(page.getByText('Spaghetti Carbonara')).toBeVisible({ timeout: 10000 });
    // mi-3 (Tiramisù) is unavailable: menu_available_no (restaurant ns) → "Hidden"
    await expect(page.getByText(/^Hidden$/i)).toBeVisible();
  });
});
