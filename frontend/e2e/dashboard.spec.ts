import { test, expect } from '@playwright/test';
import { mockAuthMe, mockDaySheet, mockDaySheetTrend, mockOwnerSummary, mockTodayArrivals, mockDueOutStays } from './fixtures/mockApi';

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
  // ArrivalsDeparturesPanel's own two calls — empty by default so specs see
  // the panel's empty state unless a test overrides with specific rows.
  await mockTodayArrivals(page);
  await mockDueOutStays(page);
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
    // All stat card labels should be present (verify by translation keys rendered as text).
    // Scoped to stats-grid: the work-list panel below also mentions arrivals,
    // which would make an unscoped match ambiguous.
    const statsGrid = page.getByTestId('stats-grid');
    await expect(statsGrid.getByText(/guests in house|ospiti in struttura/i)).toBeVisible({ timeout: 10000 });
    await expect(statsGrid.getByText(/today.*(arrivals|check.in)|arrivi/i)).toBeVisible();
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
    await expect(page.getByTestId('stats-grid').getByText(/today.*(arrivals|check.in)|arrivi/i)).toBeVisible();
  });

  test('pending revenue card visible for ADMIN role', async ({ page }) => {
    await mockOwnerSummary(page, { pendingRevenue: 150 });
    await page.goto('/');
    await expect(page.getByTestId('stats-grid')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/pending revenue|ricavi in sospeso/i)).toBeVisible();
  });

  test('each stat card is one link to its page', async ({ page }) => {
    await page.goto('/');
    const grid = page.getByTestId('stats-grid');
    await expect(grid).toBeVisible({ timeout: 10000 });
    // 4 universal stats + owner pending-revenue stat, each the whole card as one link.
    await expect(grid.getByRole('link')).toHaveCount(5);
    await expect(grid.getByRole('link', { name: /guests in house|ospiti in struttura/i })).toHaveAttribute('href', '/stays');
    await expect(grid.getByRole('link', { name: /pending revenue|fatturato in attesa/i })).toHaveAttribute('href', '/billing');
    // Left: the work-list panel's single "View all" (it follows the tab) + the room-overview one.
    await expect(page.getByRole('link', { name: /view all|vedi tutto/i })).toHaveCount(2);
  });

  test('shows the delta against yesterday when a snapshot exists', async ({ page }) => {
    const y = new Date();
    y.setDate(y.getDate() - 1);
    const yesterday = `${y.getFullYear()}-${String(y.getMonth() + 1).padStart(2, '0')}-${String(y.getDate()).padStart(2, '0')}`;
    await mockDaySheet(page, { guestsInHouse: 5 });
    await mockDaySheetTrend(page, [
      { date: yesterday, arrivals: 1, departures: 0, guestsInHouse: 2, availableRooms: 1 },
    ]);
    await page.goto('/');
    const grid = page.getByTestId('stats-grid');
    await expect(grid.getByText(/\+3.*(vs yesterday|rispetto a ieri)/i)).toBeVisible({ timeout: 10000 });
  });

  test('arrivals/departures panel switches tab and its "View all" target', async ({ page }) => {
    await page.goto('/');
    const panelLink = page.getByRole('link', { name: /view all|vedi tutto/i }).first();
    await expect(panelLink).toHaveAttribute('href', '/reservations', { timeout: 10000 });
    await page.getByRole('radio', { name: /departures|partenze/i }).click();
    await expect(panelLink).toHaveAttribute('href', '/stays');
    await expect(page.getByText(/no departures due today|nessuna partenza prevista oggi/i)).toBeVisible();
  });

  test('to-do card links dirty rooms to housekeeping and offers night audit', async ({ page }) => {
    await mockDaySheet(page, { roomStatusCounts: { CLEAN: 1, DIRTY: 3, MAINTENANCE: 0, OCCUPIED: 1 } });
    await page.goto('/');
    await expect(page.getByRole('link', { name: /3 rooms to clean|3 camere da pulire/i })).toHaveAttribute('href', '/housekeeping', { timeout: 10000 });
    await expect(page.getByTestId('today-tasks').getByRole('link', { name: /night audit/i })).toBeVisible();
  });

  test('to-do card uses the singular form for one dirty room', async ({ page }) => {
    await mockDaySheet(page, { roomStatusCounts: { CLEAN: 1, DIRTY: 1, MAINTENANCE: 0, OCCUPIED: 1 } });
    await page.goto('/');
    await expect(page.getByRole('link', { name: /1 room to clean|1 camera da pulire/i })).toBeVisible({ timeout: 10000 });
  });

  test('header offers new reservation and walk-in', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('button', { name: /new reservation|nuova prenotazione/i })).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('button', { name: /walk-in/i })).toBeVisible();
  });
});
