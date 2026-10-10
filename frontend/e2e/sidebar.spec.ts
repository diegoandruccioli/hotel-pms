import { test, expect } from '@playwright/test';
import { mockUnhandledApi, mockServerEvents, mockAuthMe, mockDaySheet, mockOwnerSummary, mockTodayArrivals, mockDueOutStays } from './fixtures/mockApi';

test.describe('Sidebar', () => {
  test.beforeEach(async ({ page }) => {
    await mockUnhandledApi(page);
    await mockServerEvents(page);
    await mockAuthMe(page);
    await mockDaySheet(page);
    await mockOwnerSummary(page);
    await page.route('**/api/v1/stays/reports/alloggiati/failures/summary', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ failedCount: 0 }) }),
    );
    await mockTodayArrivals(page);
    await mockDueOutStays(page);
  });

  test('starts expanded at 1280px, collapses with the toggle and remembers it across a reload', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/');
    await expect(page.getByTestId('dashboard-heading')).toBeVisible({ timeout: 10000 });

    const sidebar = page.locator('#app-sidebar');
    const expandedWidth = (await sidebar.boundingBox())?.width ?? 0;
    expect(expandedWidth).toBeGreaterThan(250);

    await page.getByRole('button', { name: /collapse sidebar|comprimi menu/i }).click();
    await expect.poll(async () => (await sidebar.boundingBox())?.width ?? 0).toBeLessThan(100);
    await expect(sidebar.getByRole('link', { name: /^guests$|^ospiti$/i })).toBeVisible();

    await page.reload();
    await expect(page.getByTestId('dashboard-heading')).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('button', { name: /expand sidebar|espandi menu/i })).toBeVisible();
    await expect.poll(async () => (await sidebar.boundingBox())?.width ?? 0).toBeLessThan(100);

    await page.getByRole('button', { name: /expand sidebar|espandi menu/i }).click();
    await expect.poll(async () => (await sidebar.boundingBox())?.width ?? 0).toBeGreaterThan(250);
  });

  test('starts compact below 1280px', async ({ page }) => {
    await page.setViewportSize({ width: 1100, height: 800 });
    await page.goto('/');
    await expect(page.getByTestId('dashboard-heading')).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('button', { name: /expand sidebar|espandi menu/i })).toBeVisible();
  });

  test('shows the visible name of a compact-rail link on keyboard focus and hides it on Escape', async ({ page }) => {
    await page.setViewportSize({ width: 1100, height: 800 });
    await page.goto('/');
    await expect(page.getByTestId('dashboard-heading')).toBeVisible({ timeout: 10000 });

    await page.locator('#app-sidebar').getByRole('link', { name: /^guests$|^ospiti$/i }).focus();
    await expect(page.getByTestId('m3-tooltip')).toHaveText(/guests|ospiti/i);

    await page.keyboard.press('Escape');
    await expect(page.getByTestId('m3-tooltip')).toHaveCount(0);
  });
});
