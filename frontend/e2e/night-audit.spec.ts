import { test, expect } from '@playwright/test';
import { mockAuthMe, mockDueOutStays, mockServerEvents, mockUnhandledApi } from './fixtures/mockApi';

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

const candidate = (id: string, guestFullName: string, status: string) => ({
  id, guestId: `g-${id}`, guestFullName, checkInDate: '2026-06-14', checkOutDate: '2099-01-01', status,
  expectedGuests: 2, lineItems: [{ id: `li-${id}`, roomId: 'room-1' }], active: true, version: 1,
});

const emptyPage = (content: unknown[]) => ({
  content, totalElements: content.length, totalPages: 1, number: 0, size: 50, empty: content.length === 0,
});

test.describe('Night audit', () => {
  const resolved = new Set<string>();

  test.beforeEach(async ({ page }) => {
    resolved.clear();
    await mockUnhandledApi(page);
    await mockAuthMe(page);
    await mockServerEvents(page);
    await mockDueOutStays(page);
    await page.route('**/api/v1/rooms**', (route) =>
      route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({ ...emptyPage([{ id: 'room-1', roomNumber: '201', status: 'CLEAN' }]), size: 500 }),
      }),
    );
    // Arrivals still unresolved: one PENDING and one CONFIRMED until each is patched.
    await page.route('**/api/v1/reservations/search**', (route) => {
      const status = new URL(route.request().url()).searchParams.get('status');
      const all = status === 'PENDING'
        ? [candidate('res-p', 'Pia Pending', 'PENDING')]
        : status === 'CONFIRMED' ? [candidate('res-c', 'Carl Confirmed', 'CONFIRMED')] : [];
      return route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify(emptyPage(all.filter((r) => !resolved.has(r.id)))),
      });
    });
    await page.route('**/api/v1/reservations/*/status-and-guests', (route) => {
      resolved.add(route.request().url().split('/').at(-2) ?? '');
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
    });
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

    await page.getByRole('button', { name: 'Continue' }).click();
    await page.getByRole('button', { name: 'Run closing' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();

    const run = page.waitForRequest((req) => req.method() === 'POST' && req.url().includes('/frontdesk/night-audit'));
    await dialog.getByRole('button', { name: 'Confirm' }).click();
    await run;
    await expect(dialog).toBeHidden();
  });

  test('resolves an arrival as no-show before running the closing', async ({ page }) => {
    await page.goto('/night-audit');
    await expect(page.getByText('Pia Pending')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('Carl Confirmed')).toBeVisible();

    const patch = page.waitForRequest((req) => req.method() === 'PATCH' && req.url().includes('/res-p/status-and-guests'));
    await page.getByRole('row', { name: /Pia Pending/ }).getByRole('button', { name: /^Actions for /i }).click();
    await page.getByRole('menuitem', { name: 'Mark no-show' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Confirm' }).click();
    expect((await patch).postDataJSON()).toMatchObject({ status: 'NO_SHOW' });

    await expect(page.getByText('Pia Pending')).toBeHidden();
    await expect(page.getByText('Carl Confirmed')).toBeVisible();

    await page.getByRole('button', { name: 'Continue' }).click();
    await expect(page.getByText(/1 reservation will be marked as no-show/)).toBeVisible();
  });

  test('opens the run detail dialog', async ({ page }) => {
    await page.goto('/night-audit');
    await page.getByRole('button', { name: /^View / }).click();
    await expect(page.getByRole('dialog')).toContainText('CASH');
  });
});
