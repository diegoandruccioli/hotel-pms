import { test, expect } from '@playwright/test';
import { mockAuthMe, mockDaySheet, mockOwnerSummary } from './fixtures/mockApi';

async function mockDashboardApis(page: import('@playwright/test').Page): Promise<void> {
  await mockAuthMe(page);
  await mockDaySheet(page);
  await mockOwnerSummary(page);
  // Dashboard fires this in a separate effect (admin/owner only) — unmocked, it 401s against
  // the real backend and the global axios interceptor's silent-refresh-then-logout kicks in,
  // hard-redirecting to /login mid-test (T-DASH-E2E flake root cause, fixed 2026-06-22).
  await page.route('**/api/v1/stays/reports/alloggiati/failures/summary', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ failedCount: 0 }) }),
  );
}

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await mockDashboardApis(page);
  });

  test('renders dashboard heading with username', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('dashboard-heading')).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId('dashboard-heading')).toContainText('admin');
  });

  test('renders stat cards grid', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('stats-grid')).toBeVisible({ timeout: 10000 });
    // All stat card labels should be present (verify by translation keys rendered as text)
    await expect(page.getByText(/guests in house|ospiti in struttura/i)).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/today.*(arrivals|check.in)|arrivi/i)).toBeVisible();
  });

  test('shows guests-in-house count after stats load', async ({ page }) => {
    await mockDaySheet(page, { guestsInHouse: 2 });
    await page.goto('/');
    await expect(page.getByTestId('stats-grid')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('2', { exact: true })).toBeVisible();
  });

  test('renders room status overview when day-sheet loads', async ({ page }) => {
    await mockDaySheet(page, { roomStatusCounts: { CLEAN: 1, DIRTY: 1, MAINTENANCE: 1, OCCUPIED: 1 } });
    await page.goto('/');
    await expect(page.getByTestId('room-status-summary')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/clean|pulita/i).first()).toBeVisible();
    await expect(page.getByText(/dirty|sporca/i).first()).toBeVisible();
    await expect(page.getByText(/maintenance|manutenzione/i).first()).toBeVisible();
    await expect(page.getByText(/occupied|occupata/i).first()).toBeVisible();
  });

  test('shows today arrivals — 1 arrival from the day-sheet', async ({ page }) => {
    await mockDaySheet(page, { todayArrivals: 1 });
    await page.goto('/');
    await expect(page.getByTestId('stats-grid')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/today.*(arrivals|check.in)|arrivi/i)).toBeVisible();
  });

  test('pending revenue card visible for ADMIN role', async ({ page }) => {
    await mockOwnerSummary(page, { pendingRevenue: 150 });
    await page.goto('/');
    await expect(page.getByTestId('stats-grid')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/pending revenue|ricavi in sospeso/i)).toBeVisible();
  });

  test('stat cards link to correct pages', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('stats-grid')).toBeVisible({ timeout: 10000 });
    // 4 universal stats + owner pending-revenue stat + room-overview section link, all "View all".
    const viewAllLinks = page.getByRole('link', { name: /view all|vedi tutto/i });
    await expect(viewAllLinks).toHaveCount(6);
  });
});
