import { test, expect } from '@playwright/test';
import { mockAuthMe, mockServerEvents, mockUnhandledApi } from './fixtures/mockApi';

const ADMIN = { username: 'admin', role: 'ADMIN', sub: 'admin', mustChangePassword: false } as const;
const RECEPTIONIST = { username: 'desk', role: 'RECEPTIONIST', sub: 'desk', mustChangePassword: false } as const;

test.describe('Settings area', () => {
  test('opens on the profile section and lists every section for an admin', async ({ page }) => {
    await mockAuthMe(page, ADMIN);
    await page.goto('/settings');

    await expect(page).toHaveURL(/\/settings\/profile$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Settings' })).toBeVisible();

    const nav = page.getByRole('navigation', { name: 'Settings sections' });
    await expect(nav.getByRole('link')).toHaveCount(7);
    await expect(nav.getByRole('link', { name: 'My Profile' })).toHaveAttribute('aria-current', 'page');
  });

  test('switches section from the side navigation without leaving the area', async ({ page }) => {
    await mockAuthMe(page, ADMIN);
    await page.goto('/settings/profile');

    const nav = page.getByRole('navigation', { name: 'Settings sections' });
    await nav.getByRole('link', { name: 'Change Password' }).click();

    await expect(page).toHaveURL(/\/settings\/password$/);
    await expect(nav.getByRole('link', { name: 'Change Password' })).toHaveAttribute('aria-current', 'page');
    await expect(nav.getByRole('link', { name: 'My Profile' })).not.toHaveAttribute('aria-current', 'page');
    await expect(page.getByRole('heading', { level: 1, name: 'Settings' })).toBeVisible();
  });

  test('shows front-desk staff only the personal sections', async ({ page }) => {
    await mockAuthMe(page, RECEPTIONIST);
    await page.goto('/settings/profile');

    const nav = page.getByRole('navigation', { name: 'Settings sections' });
    await expect(nav.getByRole('link')).toHaveCount(4);
    await expect(nav.getByRole('link', { name: 'System' })).toHaveCount(0);
    await expect(nav.getByRole('link', { name: 'Tourist Tax' })).toHaveCount(0);
  });

  test('sends front-desk staff away from an admin-only section', async ({ page }) => {
    await mockAuthMe(page, RECEPTIONIST);
    await page.goto('/settings/system');

    await expect(page).not.toHaveURL(/\/settings\/system/);
  });
});

test.describe('Forced password change', () => {
  const FORCED = { username: 'desk', role: 'RECEPTIONIST', sub: 'desk', mustChangePassword: true } as const;

  test('sends a user who must change the password to the change-password page and stays there', async ({ page }) => {
    const gatewayCalls: string[] = [];
    await mockUnhandledApi(page);
    await mockServerEvents(page);
    await page.route(/\/api\/v1\/(reservations|rooms|guests|stays|events)/, (route) => {
      gatewayCalls.push(route.request().url());
      return route.fulfill({ status: 403, contentType: 'application/json', body: '{"error":"PASSWORD_CHANGE_REQUIRED"}' });
    });
    await mockAuthMe(page, FORCED);

    await page.goto('/reservations');

    await expect(page).toHaveURL(/\/settings\/password$/);
    await expect(page.getByRole('alert').filter({ hasText: /must change your password/i })).toBeVisible();
    // Nothing else to wander off to: no section nav, no sidebar, no palette.
    await expect(page.getByRole('navigation', { name: 'Settings sections' })).toHaveCount(0);
    await expect(page.getByRole('navigation', { name: 'Main navigation' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /command palette/i })).toHaveCount(0);
    // And the page never asks the gateway for data it would refuse.
    expect(gatewayCalls).toEqual([]);

    // A reload on another URL still lands on the same page.
    await page.goto('/guests');
    await expect(page).toHaveURL(/\/settings\/password$/);
  });

  test('returns the user to the page they were heading to once the password is changed', async ({ page }) => {
    let changed = false;
    await mockUnhandledApi(page);
    await mockServerEvents(page);
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...FORCED, mustChangePassword: !changed }),
      }),
    );
    await page.route('**/api/v1/auth/change-password', (route) => {
      changed = true;
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
    });
    await page.route(/\/api\/v1\/reservations/, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], totalElements: 0, totalPages: 1, number: 0, size: 20 }),
      }),
    );
    await page.route(/\/api\/v1\/rooms/, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], totalElements: 0, totalPages: 1, number: 0, size: 200 }),
      }),
    );

    await page.goto('/reservations');
    await expect(page).toHaveURL(/\/settings\/password$/);

    await page.getByLabel('Current Password').fill('Temp-Pass-1!');
    await page.getByLabel('New Password', { exact: true }).fill('HotelPms@@2026xx');
    await page.getByLabel('Confirm New Password').fill('HotelPms@@2026xx');
    await page.getByRole('button', { name: 'Change Password' }).click();

    await expect(page).toHaveURL(/\/reservations$/);
  });
});
