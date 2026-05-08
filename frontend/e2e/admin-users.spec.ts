import { test, expect } from '@playwright/test';

const MOCK_ADMIN = { username: 'admin', role: 'ADMIN', sub: 'admin', mustChangePassword: false };

const MOCK_USERS = [
  {
    id: 'u-001',
    username: 'receptionist1',
    email: 'desk1@hotel.com',
    role: 'RECEPTIONIST',
    active: true,
    mustChangePassword: false,
    createdAt: '2026-01-01T10:00:00',
  },
  {
    id: 'u-002',
    username: 'reception2',
    email: 'desk2@hotel.com',
    role: 'RECEPTIONIST',
    active: false,
    mustChangePassword: true,
    createdAt: '2026-02-01T10:00:00',
  },
];

const NEW_USER = {
  id: 'u-003',
  username: 'newstaff',
  email: 'newstaff@hotel.com',
  role: 'RECEPTIONIST',
  active: true,
  mustChangePassword: true,
  createdAt: new Date().toISOString(),
};

test.describe('Admin Users management', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_ADMIN) }),
    );
    await page.route('**/api/v1/auth/users', (route) => {
      if (route.request().method() === 'GET') {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(MOCK_USERS),
        });
      }
      if (route.request().method() === 'POST') {
        return route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(NEW_USER),
        });
      }
      return route.continue();
    });
  });

  test('renders user table with existing users', async ({ page }) => {
    await page.goto('/admin/users');
    await expect(page.getByRole('heading', { name: /user management/i })).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('receptionist1')).toBeVisible();
    await expect(page.getByText('reception2')).toBeVisible();
  });

  test('shows active/inactive status badges', async ({ page }) => {
    await page.goto('/admin/users');
    await expect(page.getByText('active')).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('inactive')).toBeVisible();
  });

  test('shows must-change-password warning for flagged user', async ({ page }) => {
    await page.goto('/admin/users');
    await expect(page.getByText(/must change password/i)).toBeVisible({ timeout: 5000 });
  });

  test('opens create user modal and submits', async ({ page }) => {
    await page.goto('/admin/users');
    await page.getByRole('button', { name: /new user/i }).click();
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: 3000 });

    await page.locator('#new-username').fill('newstaff');
    await page.locator('#new-email').fill('newstaff@hotel.com');
    await page.locator('#new-password').fill('SecurePass123');
    await page.locator('#new-role').selectOption('RECEPTIONIST');

    await page.getByRole('button', { name: /^create$/i }).click();

    // Modal should close and new user should appear
    await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 3000 });
    await expect(page.getByText('newstaff')).toBeVisible();
  });

  test('shows error in modal when fields are empty', async ({ page }) => {
    await page.goto('/admin/users');
    await page.getByRole('button', { name: /new user/i }).click();
    await page.getByRole('button', { name: /^create$/i }).click();
    await expect(page.getByRole('alert')).toBeVisible();
  });

  test('deactivates a user', async ({ page }) => {
    await page.route('**/api/v1/auth/users/u-001/deactivate', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...MOCK_USERS[0], active: false }),
      }),
    );

    await page.goto('/admin/users');
    const firstDeactivateBtn = page.getByRole('button', { name: /deactivate/i }).first();
    await firstDeactivateBtn.click();

    // The user row should now show inactive
    await expect(page.getByText('inactive').first()).toBeVisible({ timeout: 3000 });
  });

  test('is not accessible to non-admin — redirects', async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ username: 'desk', role: 'RECEPTIONIST', sub: 'desk', mustChangePassword: false }),
      }),
    );
    await page.goto('/admin/users');
    await expect(page).not.toHaveURL(/\/admin\/users/);
  });
});
