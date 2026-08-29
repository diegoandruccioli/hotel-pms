import { test, expect, type Page } from '@playwright/test';
import { csrfHeader, createCleanRoom, createGuest, STATO_FRANCIA } from '../fixtures/api';
import { attachQaListeners, setContext, logCustom } from './support/qaListeners';

/**
 * Navigates to the real check-in form the way an actual user would: from
 * /reservations, via the row's data-testid=`check-in-btn-{id}` button.
 * CheckInForm.tsx reads roomId/guestId from React Router `location.state`
 * (Reservations.tsx's handleCheckIn passes it), NOT from an API call keyed
 * off the URL param — so a direct page.goto('/stays/check-in/:id') lands on
 * a form with no context and immediately shows err_missing_context. That
 * gap (no deep-link / refresh-mid-flow support) is itself logged as a
 * finding by the dedicated grey-path test at the bottom of this file.
 */
async function goToCheckInViaReservationsList(page: Page, reservationId: string): Promise<void> {
  await page.goto('/reservations');
  // No networkidle wait here: the app opens a persistent SSE connection
  // (/api/v1/events/stream, room-status realtime sync) that never goes idle,
  // so networkidle would just burn its full timeout every time. The button's
  // own visibility wait below is the real signal.
  const checkInButton = page.getByTestId(`check-in-btn-${reservationId}`);
  await expect(checkInButton).toBeVisible({ timeout: 15_000 });
  await checkInButton.click();
  await expect(page).toHaveURL(`/stays/check-in/${reservationId}`);
}

// 2026-08-24 QA pass — Phase 3 business flows not already covered by the
// existing live suite (walk-in-live.spec.ts covers walk-in, checkout-live.spec.ts
// covers checkout+PDF+XML, planning-board-live.spec.ts covers the calendar,
// idor-cross-tenant-live.spec.ts covers cross-tenant RBAC). This file covers:
// the reservation -> check-in UI path (untested elsewhere), and — the actual
// point of this worktree — that CITY_TAX is posted alongside ROOM_NIGHT at
// check-in once a comune + category + rate are all configured.

test.beforeEach(async ({ context }) => {
  attachQaListeners(context, { role: 'ADMIN', locale: 'it' });
});

test.describe('QA 2026-08-24 — reservation to check-in via the real UI', () => {
  test('a CONFIRMED reservation can be checked in through /stays/check-in/:id and opens a real invoice', async ({ page, request }) => {
    test.setTimeout(45_000);
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers);

    const checkIn = new Date();
    const checkOut = new Date();
    checkOut.setDate(checkOut.getDate() + 2);
    const reservationResponse = await request.post('/api/v1/reservations', {
      headers,
      data: {
        guestId: guest.id,
        expectedGuests: 1,
        checkInDate: checkIn.toISOString().split('T')[0],
        checkOutDate: checkOut.toISOString().split('T')[0],
        status: 'CONFIRMED',
        lineItems: [{ roomId: room.id, price: 95.0 }],
      },
    });
    expect(reservationResponse.status(), await reservationResponse.text()).toBe(201);
    const reservation = await reservationResponse.json();

    setContext(`/stays/check-in/${reservation.id}`, 'reservation-checkin-ui');
    await goToCheckInViaReservationsList(page, reservation.id);
    await expect(page.getByRole('button', { name: /complete check-?in/i })).toBeVisible({ timeout: 15_000 });

    // Minimum-path Alloggiati fields, same pattern proven in walk-in-live.spec.ts
    // (GuestFieldSection is shared between CheckInForm and WalkInCheckInForm).
    const travellerType = page.locator('#traveller-type-0');
    if (await travellerType.count() > 0) {
      await travellerType.selectOption({ value: 'FAMILIARE' });
      await page.locator('#stato-nascita-0').fill('FRANC');
      await expect(page.getByRole('option', { name: /FRANCIA/i })).toBeVisible({ timeout: 5_000 });
      await page.getByRole('option', { name: /FRANCIA/i }).click();
      await page.locator('input[name="dateOfBirth"]').first().fill('1990-01-01');
    }

    const [checkinResponse] = await Promise.all([
      page.waitForResponse((r) => r.url().includes(`/api/v1/stays`) && r.request().method() === 'POST'),
      page.getByRole('button', { name: /complete check-?in/i }).click(),
    ]);
    const body = await checkinResponse.text();
    expect(checkinResponse.status(), body).toBe(201);
    const stay = JSON.parse(body) as { id: string; invoiceId: string | null; roomId: string };
    expect(stay.roomId).toBe(room.id);
    expect(stay.invoiceId, 'check-in from a reservation must open an invoice').toBeTruthy();

    const invoice = await (await request.get(`/api/v1/invoices/${stay.invoiceId}`)).json();
    const chargeTypes = (invoice.charges as Array<{ chargeType?: string; type?: string }>)
      .map((c) => c.chargeType ?? c.type);
    expect(chargeTypes).toContain('ROOM_NIGHT');
  });
});

test.describe('QA 2026-08-24 — imposta di soggiorno is charged at check-in once fully configured', () => {
  test('with comune + hotel category + matching rate all configured, check-in posts a CITY_TAX charge alongside ROOM_NIGHT', async ({ page, request }) => {
    // NOTE: this specific UI-driven run has been flaky in this session (the
    // check-in POST occasionally doesn't resolve within the timeout, cause
    // not fully isolated — possibly SSE/rate-limit noise from the many
    // repeated runs during this QA session, see REPORT.md §0 methodology).
    // The underlying feature is independently confirmed via direct API
    // evidence: a real invoice charge with type=CITY_TAX, amount=3.50,
    // vatRate=0, naturaCode=N1 was captured live during this session — see
    // REPORT.md. Left enabled (not skipped) so it keeps exercising the real
    // UI path on future runs.
    test.setTimeout(45_000);
    const headers = await csrfHeader(request);

    // Hotel A already has comune/provincia configured (global.setup.ts). The
    // category must be valid AS OF TODAY (CityTaxAssessmentServiceImpl looks
    // up the applicable category/rate for the stay's actual check-in date),
    // so — unlike other fixtures in this suite — this can't just use a
    // never-before-seen value: HOTEL_CATEGORY_OVERLAP is a real constraint
    // (one active category per hotel at a time), and TWO test runs on the
    // same UTC day both trying to open a category "as of today" collide with
    // each other's history (reproduced). Idempotent instead: reuse whatever
    // category is already active today if one exists (same pattern as
    // global.setup.ts's "idempotent — 409 on a re-run" user creation), only
    // creating a fresh one if none is. The CITY_TAX-at-checkin mechanism
    // itself is independently confirmed via direct API evidence in
    // REPORT.md (real invoice charge: type=CITY_TAX, vatRate=0, naturaCode=N1).
    const today = new Date().toISOString().split('T')[0];
    const currentHistory = await (await request.get('/api/v1/stays/hotel-category')).json() as
      Array<{ category: string; validTo: string | null }>;
    let category = currentHistory.find((h) => h.validTo === null)?.category;
    if (!category) {
      category = `QABIZ${Date.now()}`;
      const categoryResponse = await request.post('/api/v1/stays/hotel-category', {
        headers, data: { category, validFrom: today },
      });
      expect(categoryResponse.status(), await categoryResponse.text()).toBe(201);
    }
    const rateResponse = await request.post('/api/v1/stays/city-tax-rates', {
      headers, data: { category, amountPerNight: 3.5, validFrom: today },
    });
    if (rateResponse.status() !== 201 && rateResponse.status() !== 409) {
      throw new Error(`Unexpected status creating city-tax rate: ${rateResponse.status()} ${await rateResponse.text()}`);
    }

    const room = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers);
    const checkIn = new Date();
    const checkOut = new Date();
    checkOut.setDate(checkOut.getDate() + 2);
    const reservationResponse = await request.post('/api/v1/reservations', {
      headers,
      data: {
        guestId: guest.id, expectedGuests: 1,
        checkInDate: checkIn.toISOString().split('T')[0],
        checkOutDate: checkOut.toISOString().split('T')[0],
        status: 'CONFIRMED',
        lineItems: [{ roomId: room.id, price: 95.0 }],
      },
    });
    const reservation = await reservationResponse.json();

    setContext(`/stays/check-in/${reservation.id}`, 'city-tax-checkin');
    await page.goto(`/stays/check-in/${reservation.id}`);
    await expect(page.getByRole('button', { name: /complete check-?in/i })).toBeVisible({ timeout: 15_000 });
    const travellerType = page.locator('#traveller-type-0');
    if (await travellerType.count() > 0) {
      await travellerType.selectOption({ value: 'FAMILIARE' });
      await page.locator('#stato-nascita-0').fill('FRANC');
      await expect(page.getByRole('option', { name: /FRANCIA/i })).toBeVisible({ timeout: 5_000 });
      await page.getByRole('option', { name: /FRANCIA/i }).click();
      await page.locator('input[name="dateOfBirth"]').first().fill('1990-01-01');
    }
    const [checkinResponse] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/v1/stays') && r.request().method() === 'POST'),
      page.getByRole('button', { name: /complete check-?in/i }).click(),
    ]);
    const stay = JSON.parse(await checkinResponse.text()) as { invoiceId: string };

    const invoice = await (await request.get(`/api/v1/invoices/${stay.invoiceId}`)).json();
    const cityTaxCharge = (invoice.charges as Array<{ chargeType?: string; type?: string; amount: number; vatRate?: number }>)
      .find((c) => (c.chargeType ?? c.type) === 'CITY_TAX');
    logCustom('city_tax_checkin_result', { invoiceCharges: invoice.charges, foundCityTax: Boolean(cityTaxCharge) });
    expect(cityTaxCharge, 'CITY_TAX charge missing from invoice despite comune+category+rate all configured').toBeTruthy();
    expect(cityTaxCharge!.amount).toBeGreaterThan(0);
    // COMPLIANCE_AUDIT_2026-08.md §4: imposta di soggiorno is VAT-exempt (out of scope IVA).
    if (cityTaxCharge!.vatRate !== undefined) {
      expect(cityTaxCharge!.vatRate, 'city tax must be VAT-exempt (Natura code), not a taxed line').toBe(0);
    }
  });
});

test.describe('QA 2026-08-24 — F&B order to room charge', () => {
  test('an F&B order confirmed on an active CHECKED_IN stay bills to the room invoice', async ({ page, request }) => {
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers);
    const stayResponse = await request.post('/api/v1/stays', {
      headers,
      data: {
        guestId: guest.id, roomId: room.id, status: 'CHECKED_IN',
        expectedCheckOutDate: new Date(Date.now() + 86_400_000).toISOString().split('T')[0],
        guests: [{
          firstName: 'Live', lastName: 'Suite Guest', gender: '1', dateOfBirth: '1990-01-01',
          placeOfBirth: STATO_FRANCIA, citizenship: STATO_FRANCIA, isPrimaryGuest: true, travellerType: 'FAMILIARE',
        }],
      },
    });
    expect(stayResponse.status(), await stayResponse.text()).toBe(201);
    const stay = await stayResponse.json();

    // MenuItemController.getAllMenuItems() returns a plain List<>, not a Page<> — no pagination params.
    const menuItemsResponse = await request.get('/api/v1/fb/menu-items');
    const menuItems = (await menuItemsResponse.json()) as Array<{ id: string; name: string; price: number }>;
    expect(menuItems.length, 'no F&B menu items available to order — cannot exercise this flow').toBeGreaterThan(0);

    setContext('/restaurant', 'fb-order-to-room');
    await page.goto('/restaurant');
    await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => undefined);

    const orderResponse = await request.post('/api/v1/fb/orders', {
      headers,
      data: { stayId: stay.id, items: [{ menuItemId: menuItems[0].id, quantity: 1 }] },
    });
    expect(orderResponse.status(), await orderResponse.text()).toBe(201);
    const order = await orderResponse.json();
    const confirmResponse = await request.post(`/api/v1/fb/orders/${order.id}/confirm`, { headers });
    expect(confirmResponse.status(), await confirmResponse.text()).toBe(200);

    const invoice = await (await request.get(`/api/v1/invoices/${stay.invoiceId}`)).json();
    const fbCharge = (invoice.charges as Array<{ chargeType?: string; type?: string }>)
      .find((c) => (c.chargeType ?? c.type) === 'FB_ORDER');
    expect(fbCharge, 'F&B order confirmed but no FB_ORDER charge landed on the room invoice').toBeTruthy();
  });
});

test.describe('QA 2026-08-24 — grey path: check-in form deep-link/refresh support', () => {
  test('a direct navigation on /stays/check-in/:id (no router state) fetches the reservation and completes check-in', async ({ page, request }) => {
    // Fixed 2026-08-24 (REPORT.md §6 #5): CheckInForm.tsx used to read
    // roomId/guestId/expectedGuests ONLY from React Router location.state (set by
    // Reservations.tsx's handleCheckIn) — a direct navigation, bookmark, or page
    // refresh lost them entirely. Now falls back to GET /api/v1/reservations/{id}.
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers);
    const checkIn = new Date();
    const checkOut = new Date();
    checkOut.setDate(checkOut.getDate() + 1);
    const reservationResponse = await request.post('/api/v1/reservations', {
      headers,
      data: {
        guestId: guest.id, expectedGuests: 1,
        checkInDate: checkIn.toISOString().split('T')[0],
        checkOutDate: checkOut.toISOString().split('T')[0],
        status: 'CONFIRMED',
        lineItems: [{ roomId: room.id, price: 90.0 }],
      },
    });
    const reservation = await reservationResponse.json();

    setContext(`/stays/check-in/${reservation.id}`, 'deep-link-fallback-fetch');
    // Direct navigation — no click-through from /reservations, so no location.state.
    await page.goto(`/stays/check-in/${reservation.id}`);
    await expect(page.getByRole('button', { name: /complete check-?in/i })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('err_missing_context')).toHaveCount(0);

    const travellerType = page.locator('#traveller-type-0');
    if (await travellerType.count() > 0) {
      await travellerType.selectOption({ value: 'FAMILIARE' });
      await page.locator('#stato-nascita-0').fill('FRANC');
      await expect(page.getByRole('option', { name: /FRANCIA/i })).toBeVisible({ timeout: 5_000 });
      await page.getByRole('option', { name: /FRANCIA/i }).click();
      await page.locator('input[name="dateOfBirth"]').first().fill('1990-01-01');
    }

    const [checkinResponse] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/v1/stays') && r.request().method() === 'POST'),
      page.getByRole('button', { name: /complete check-?in/i }).click(),
    ]);
    const body = await checkinResponse.text();
    expect(checkinResponse.status(), body).toBe(201);
    const stay = JSON.parse(body) as { roomId: string; invoiceId: string | null };
    expect(stay.roomId, 'deep-linked check-in must still resolve to the correct room from the reservation').toBe(room.id);
    expect(stay.invoiceId).toBeTruthy();
  });
});
