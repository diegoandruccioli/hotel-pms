import { test, expect } from '@playwright/test';
import { mockAuthMe } from './fixtures/mockApi';

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
