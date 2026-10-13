import { test, expect } from '@playwright/test';

test.describe('Login page', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) => route.fulfill({ status: 401, body: '' }));
    await page.route('**/api/v1/auth/refresh', (route) => route.fulfill({ status: 401, body: '' }));
    await page.goto('/login');
    await expect(page.locator('[data-testid="login-form"]')).toBeVisible();
  });

  test('titles the page and shows the brand panel on a wide screen', async ({ page }) => {
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByRole('complementary')).toBeVisible();
  });

  test('switches to high contrast before signing in', async ({ page }) => {
    const chip = page.getByRole('group', { name: /display preferences|preferenze di visualizzazione/i })
      .getByRole('button', { name: /high contrast|alto contrasto/i });
    await expect(chip).toHaveAttribute('aria-pressed', 'false');

    await chip.click();

    await expect(chip).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator('html')).toHaveAttribute('data-contrast', 'high');
  });

  test('keeps <html lang> in step with the chosen language', async ({ page }) => {
    const group = page.getByRole('group', { name: /display preferences|preferenze di visualizzazione/i });

    await group.getByRole('button', { name: 'Italiano' }).click();
    await expect(page.locator('html')).toHaveAttribute('lang', 'it');

    await group.getByRole('button', { name: 'English' }).click();
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  });

  test('reveals and hides the password', async ({ page }) => {
    const field = page.locator('input[name="password"]');
    await expect(field).toHaveAttribute('type', 'password');

    await page.getByRole('button', { name: /show password|mostra password/i }).click();
    await expect(field).toHaveAttribute('type', 'text');

    await page.getByRole('button', { name: /hide password|nascondi password/i }).click();
    await expect(field).toHaveAttribute('type', 'password');
  });

  test('hides the brand panel on a phone and keeps the form usable', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(page.getByRole('complementary')).toBeHidden();
    await expect(page.locator('[data-testid="login-submit"]')).toBeVisible();
  });
});
