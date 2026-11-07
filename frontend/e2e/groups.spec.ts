import { test, expect } from '@playwright/test';
import { mockAuthMe, mockServerEvents, mockUnhandledApi } from './fixtures/mockApi';

const member = (overrides: Record<string, unknown>) => ({
  guestId: 'g1', roomId: 'room-1', expectedGuests: 2, actualGuests: 0,
  checkInDate: '2026-10-01', checkOutDate: '2026-10-03', status: 'CONFIRMED', billedToMasterFolio: false, price: 200,
  ...overrides,
});

const GROUP = {
  id: 'group-1',
  name: 'Congresso Medico',
  companyName: 'Acme',
  contactGuestId: 'g0',
  contactGuestName: 'Elena Fabbri',
  checkInDate: '2026-10-01',
  checkOutDate: '2026-10-03',
  status: 'CONFIRMED',
  groupRatePerNight: 100,
  masterFolioInvoiceId: null,
  notes: null,
  members: [
    member({ reservationId: 'res-1', guestFullName: 'Jane Doe' }),
    member({ reservationId: 'res-2', guestId: 'g2', guestFullName: 'Mario Rossi', roomId: 'room-2', status: 'PENDING' }),
    member({ reservationId: 'res-3', guestId: 'g3', guestFullName: 'Anna Serra', roomId: null }),
  ],
  active: true,
  createdAt: '2026-09-01T10:00:00',
  updatedAt: '2026-09-01T10:00:00',
  version: 0,
};

const ROOMS = [
  { id: 'room-1', roomNumber: '201', status: 'CLEAN', roomType: { name: 'Doppia', basePrice: 120 } },
  { id: 'room-2', roomNumber: '202', status: 'CLEAN', roomType: { name: 'Singola', basePrice: 80 } },
];

test.describe('Reservation group detail', () => {
  test.beforeEach(async ({ page }) => {
    await mockUnhandledApi(page);
    await mockAuthMe(page);
    await mockServerEvents(page);
    await page.route('**/api/v1/rooms**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: ROOMS, totalElements: ROOMS.length, totalPages: 1, number: 0, size: 500 }),
      }),
    );
    await page.route('**/api/v1/reservation-groups/group-1', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(GROUP) }),
    );
  });

  test('shows the rooming list with room, status per member and progress', async ({ page }) => {
    await page.goto('/reservations/groups/group-1');
    const list = page.getByRole('region', { name: 'Rooming list' });
    await expect(list).toBeVisible({ timeout: 10000 });
    await expect(list.getByText('1 of 3 rooms ready')).toBeVisible();
    await expect(list.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '1');

    const jane = list.getByRole('row', { name: /Jane Doe/ });
    await expect(jane.getByText('201')).toBeVisible();
    await expect(jane.getByText('Doppia')).toBeVisible();
    await expect(jane.getByText('Confirmed')).toBeVisible();
    await expect(list.getByRole('row', { name: /Mario Rossi/ }).getByText('Pending')).toBeVisible();
    await expect(list.getByRole('row', { name: /Anna Serra/ }).getByText('Unassigned')).toBeVisible();
  });

  test('check-in is offered only for confirmed members and opens the check-in form', async ({ page }) => {
    await page.route('**/api/v1/reservations/res-1', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: '{}' }),
    );
    await page.goto('/reservations/groups/group-1');
    const list = page.getByRole('region', { name: 'Rooming list' });
    await expect(list).toBeVisible({ timeout: 10000 });
    await expect(list.getByRole('row', { name: /Mario Rossi/ }).getByRole('button', { name: 'Check In' })).toHaveCount(0);
    await list.getByRole('row', { name: /Jane Doe/ }).getByRole('button', { name: 'Check In' }).click();
    await expect(page).toHaveURL(/\/stays\/check-in\/res-1/);
  });
});
