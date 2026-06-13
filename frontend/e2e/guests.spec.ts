import { test, expect } from '@playwright/test';

const MOCK_USER = { username: 'admin', role: 'ADMIN', sub: 'admin', mustChangePassword: false };

const MOCK_GUESTS = [
  {
    id: 'g-001',
    firstName: 'Mario',
    lastName: 'Rossi',
    email: 'mario.rossi@test.com',
    phone: '+39 333 1234567',
    city: 'Roma',
    country: 'IT',
    identityDocuments: [],
    createdAt: '2026-01-01T10:00:00',
    updatedAt: '2026-01-01T10:00:00',
  },
  {
    id: 'g-002',
    firstName: 'Anna',
    lastName: 'Bianchi',
    email: 'anna.bianchi@test.com',
    phone: null,
    city: 'Milano',
    country: 'IT',
    identityDocuments: [],
    createdAt: '2026-02-01T10:00:00',
    updatedAt: '2026-02-01T10:00:00',
  },
];

const NEW_GUEST = {
  id: 'g-003',
  firstName: 'Luca',
  lastName: 'Verdi',
  email: 'luca.verdi@test.com',
  phone: null,
  city: 'Firenze',
  country: 'IT',
  identityDocuments: [],
  createdAt: '2026-03-01T10:00:00',
  updatedAt: '2026-03-01T10:00:00',
};

const guestPage = {
  content: MOCK_GUESTS,
  totalElements: MOCK_GUESTS.length,
  totalPages: 1,
  number: 0,
  size: 20,
};

test.describe('Guests', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_USER) }),
    );
    await page.route('**/api/v1/guests', async (route) => {
      const method = route.request().method();
      if (method === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(guestPage) });
      } else if (method === 'POST') {
        await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(NEW_GUEST) });
      } else {
        await route.fallback();
      }
    });
    await page.route('**/api/v1/guests/search**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [MOCK_GUESTS[0]], totalElements: 1, totalPages: 1, number: 0, size: 20 }),
      }),
    );
    await page.route('**/api/v1/guests/g-001', async (route) => {
      const method = route.request().method();
      if (method === 'PUT') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...MOCK_GUESTS[0], city: 'Napoli' }) });
      } else if (method === 'DELETE') {
        await route.fulfill({ status: 204 });
      } else {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_GUESTS[0]) });
      }
    });
  });

  test('renders guest list with all guests', async ({ page }) => {
    await page.goto('/guests');
    await expect(page.getByText('Mario Rossi')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Anna Bianchi')).toBeVisible();
    await expect(page.getByText('mario.rossi@test.com')).toBeVisible();
  });

  test('shows Add Guest button for admin', async ({ page }) => {
    await page.goto('/guests');
    await expect(page.getByRole('button', { name: /add guest|aggiungi ospite/i })).toBeVisible({ timeout: 10000 });
  });

  test('opens create guest modal on button click', async ({ page }) => {
    await page.goto('/guests');
    await page.getByRole('button', { name: /add guest|aggiungi ospite/i }).click();
    // Modal dialog should appear
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: 3000 });
  });

  test('creates new guest via modal', async ({ page }) => {
    await page.goto('/guests');
    await page.getByRole('button', { name: /add guest|aggiungi ospite/i }).click();
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: 3000 });

    // Fill in required fields
    await page.locator('#firstName').fill('Luca');
    await page.locator('#lastName').fill('Verdi');
    await page.locator('#email').fill('luca.verdi@test.com');

    // Submit the form
    await page.getByRole('button', { name: /save|salva/i }).click();

    // Modal should close after successful save
    await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 5000 });
  });

  test('opens edit modal for existing guest', async ({ page }) => {
    await page.goto('/guests');
    await expect(page.getByText('Mario Rossi')).toBeVisible({ timeout: 10000 });
    // Click first Edit button
    await page.getByRole('button', { name: /edit/i }).first().click();
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: 3000 });
    // Modal pre-fills first name
    await expect(page.locator('#firstName')).toHaveValue('Mario');
  });

  test('shows delete confirmation for admin', async ({ page }) => {
    await page.goto('/guests');
    await expect(page.getByText('Mario Rossi')).toBeVisible({ timeout: 10000 });
    await page.getByRole('button', { name: /edit/i }).first().click();
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: 3000 });
    // Delete button inside the dialog only — avoids strict mode with list row Delete buttons
    await expect(page.getByRole('dialog').getByRole('button', { name: /delete/i })).toBeVisible();
  });

  test('client-side search filters guest list', async ({ page }) => {
    await page.goto('/guests');
    await expect(page.getByText('Mario Rossi')).toBeVisible({ timeout: 10000 });

    const searchInput = page.getByRole('searchbox');
    await searchInput.fill('mario');

    // After 300ms debounce + re-render, only Mario should be visible
    await expect(page.getByText('Mario Rossi')).toBeVisible({ timeout: 2000 });
    await expect(page.getByText('Anna Bianchi')).not.toBeVisible();
  });
});
