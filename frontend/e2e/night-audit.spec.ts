import { test, expect } from '@playwright/test';
import { mockAuthMe, mockTodayArrivals, mockDueOutStays } from './fixtures/mockApi';

const COMPLETED_RUN = {
  id: 'run-1',
  businessDate: '2026-06-15',
  status: 'COMPLETED',
  startedAt: '2026-06-16T03:30:00',
  completedAt: '2026-06-16T03:30:05',
  runBy: 'admin',
  arrivals: 7,
  departures: 5,
  guestsInHouse: 21,
  currentStays: 9,
  availableRooms: 3,
  noShowsMarked: 1,
  cashByMethod: [{ paymentMethod: 'CASH', total: 150 }],
  cashSummaryDegraded: false,
  failureReason: null,
};

const historyPage = {
  content: [COMPLETED_RUN],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

test.describe('Night audit', () => {
  test.beforeEach(async ({ page }) => {
    await mockAuthMe(page);
    // The pre-check inside the closing card queries both; unmocked, they 401
    // and the axios interceptor sends the whole page to /login.
    await mockTodayArrivals(page);
    await mockDueOutStays(page);
    await page.route('**/api/v1/frontdesk/night-audit**', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(COMPLETED_RUN) });
      } else {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(historyPage) });
      }
    });
  });

  test('shows the closing card, the last closing summary and the history', async ({ page }) => {
    await page.goto('/night-audit');
    await expect(page.getByRole('heading', { name: 'Night Audit', level: 1 })).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'Close a business day' })).toBeVisible();
    await expect(page.getByRole('heading', { name: /Last closing/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /^View / })).toBeVisible();
  });

  test('runs the closing after confirmation', async ({ page }) => {
    await page.goto('/night-audit');
    await expect(page.getByRole('heading', { name: 'Close a business day' })).toBeVisible({ timeout: 10000 });

    await page.getByRole('button', { name: 'Run closing' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();

    const run = page.waitForRequest((req) => req.method() === 'POST' && req.url().includes('/frontdesk/night-audit'));
    await dialog.getByRole('button', { name: 'Confirm' }).click();
    await run;
    await expect(dialog).toBeHidden();
  });

  test('opens the run detail dialog', async ({ page }) => {
    await page.goto('/night-audit');
    await page.getByRole('button', { name: /^View / }).click();
    await expect(page.getByRole('dialog')).toContainText('CASH');
  });
});
