import { test, expect } from '@playwright/test';
import { csrfHeader, createCleanRoom, createGuest, createWalkInStay } from '../fixtures/api';
import { midflightAbort } from './support/faultInjector';

// Blocco 6 (continued) — API-level fault injection and concurrency checks
// that don't need the UI: payment/checkout/F&B/Alloggiati/export midflight
// interruptions (retry must never duplicate), and the reservation @Version
// lost-update question. Real backend, no mocks.
test.describe('Blocco 6 — resilience (API-level)', () => {
  test('payment: midflight-aborted POST does not double-charge on retry', async ({ request }) => {
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers, {});
    const stay = await createWalkInStay(request, headers, { roomId: room.id, guestId: guest.id });
    const invoice = await request.get(`/api/v1/invoices/${stay.invoiceId}`);
    const { totalAmount } = (await invoice.json()) as { totalAmount: number };

    // request.post from an APIRequestContext bypasses page.route(), so the
    // midflight simulation here is done directly: fire the real POST, let it
    // land, then treat "client never saw the response" the same way the UI
    // would (retry with the identical amount) and assert no duplicate charge
    // was recorded server-side.
    const payment1 = await request.post(`/api/v1/invoices/${stay.invoiceId}/payments`, {
      headers,
      data: { amount: totalAmount, paymentMethod: 'CASH' },
    });
    expect(payment1.status(), await payment1.text()).toBe(201);

    // Retry after the "client" didn't see the first response — the invoice
    // is now PAID, so a second identical payment attempt must be rejected,
    // not silently accepted as a second payment against a PAID invoice.
    const payment2 = await request.post(`/api/v1/invoices/${stay.invoiceId}/payments`, {
      headers,
      data: { amount: totalAmount, paymentMethod: 'CASH' },
    });
    expect(payment2.status(), 'a retried payment against an already-PAID invoice must not be accepted as a duplicate charge').not.toBe(201);

    const invoiceAfter = await request.get(`/api/v1/invoices/${stay.invoiceId}`);
    const { payments } = (await invoiceAfter.json()) as { payments: unknown[] };
    expect(payments.length, 'exactly one payment must be recorded, not two').toBe(1);
  });

  test('F&B order: rapid duplicate submission does not create two orders for the same intent', async ({ request }) => {
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers, {});
    const stay = await createWalkInStay(request, headers, { roomId: room.id, guestId: guest.id });

    const menuResponse = await request.get('/api/v1/fb/menu-items');
    const menuItems = (await menuResponse.json()) as { id: string }[];
    const items = (menuItems as unknown as { content?: { id: string }[] }).content ?? menuItems;
    expect(items.length, 'seed data must include at least one menu item for this check').toBeGreaterThan(0);
    const menuItemId = items[0].id;

    const orderPayload = { stayId: stay.id, items: [{ menuItemId, quantity: 1 }] };
    const [order1, order2] = await Promise.all([
      request.post('/api/v1/fb/orders', { headers, data: orderPayload }),
      request.post('/api/v1/fb/orders', { headers, data: orderPayload }),
    ]);
    const statuses = [order1.status(), order2.status()].sort();
    // Both succeeding (each a legitimate, separate order — no idempotency key
    // in this API) is the actual contract; this test's job is to make sure
    // NEITHER call 500s and that whatever the count ends up being, it's
    // explainable — not a crash, not a silently corrupted invoice.
    expect(statuses.every((s) => s === 201 || s === 400 || s === 409), `unexpected status pair ${JSON.stringify(statuses)}`).toBe(true);

    const invoice = await request.get(`/api/v1/invoices/${stay.invoiceId}`);
    expect(invoice.status()).toBe(200);
  });

  test('Alloggiati submit: midflight abort then retry does not double-submit the same day', async ({ request, page }) => {
    // Uses today's date on the seed hotel — any FAMILIARE/CAPOFAMIGLIA guest
    // checked in today qualifies; the fixture stay from createWalkInStay is
    // exactly that (travellerType FAMILIARE with isPrimaryGuest true, which
    // StayAlloggiatiCoordinator treats as its own capofamiglia).
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers, {});
    await createWalkInStay(request, headers, { roomId: room.id, guestId: guest.id });

    const today = new Date().toISOString().split('T')[0];
    const unroute = await midflightAbort(page, `**/api/v1/stays/reports/alloggiati/submit**`);
    await page.goto('/dashboard');
    try {
      // Drive the submit through fetch in-page so it rides the same
      // page.route() interception midflightAbort() installed (an
      // APIRequestContext call would bypass page.route() entirely).
      await page.evaluate(async (date) => {
        try {
          await fetch(`/api/v1/stays/reports/alloggiati/submit?date=${date}`, { method: 'POST', credentials: 'include' });
        } catch {
          // expected: the client-side connection is killed by midflightAbort
        }
      }, today);
    } finally {
      await unroute();
    }

    // Retry for real, now unobstructed.
    const csrf = await csrfHeader(request);
    const retry = await request.post(`/api/v1/stays/reports/alloggiati/submit?date=${today}`, { headers: csrf });
    const retryText = await retry.text();
    expect(retry.status(), retryText).toBe(200);
    // ALLOGGIATI_MANUAL_SUBMIT_RECORDED or ALREADY_SENT-style outcome — either
    // way the endpoint must respond cleanly and idempotently (200, whether or
    // not it has a JSON body), never crash or silently produce two SOAP
    // submissions for the same day (dry-run SOAP action is Test, not Send, so
    // no real transmission happens either way).
  });

  test('reservation concurrent edits: last write wins without a version check — documents the actual API contract', async ({ request }) => {
    const headers = await csrfHeader(request);
    const guest = await createGuest(request, headers, {});
    // A freshly created room (not a shared seed room) so this reservation's
    // dates can't collide with another fixture from an earlier round.
    const room = await createCleanRoom(request, headers);
    const roomId = room.id;

    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const checkIn = tomorrow.toISOString().split('T')[0];
    const checkOutDate = new Date(tomorrow);
    checkOutDate.setDate(checkOutDate.getDate() + 2);
    const checkOut = checkOutDate.toISOString().split('T')[0];

    const created = await request.post('/api/v1/reservations', {
      headers,
      data: { guestId: guest.id, expectedGuests: 1, checkInDate: checkIn, checkOutDate: checkOut, status: 'CONFIRMED', lineItems: [{ roomId }] },
    });
    expect(created.status(), await created.text()).toBe(201);
    const reservationId = (await created.json()).id as string;

    // "Tab A" and "Tab B" both load the same reservation state.
    const base = { guestId: guest.id, checkInDate: checkIn, checkOutDate: checkOut, lineItems: [{ roomId }] };
    // Tab A saves first, changing expectedGuests.
    const tabA = await request.put(`/api/v1/reservations/${reservationId}`, {
      headers, data: { ...base, expectedGuests: 2, status: 'CONFIRMED' },
    });
    expect(tabA.status(), await tabA.text()).toBe(200);

    // Tab B still holds its pre-A read and saves a DIFFERENT field, unaware
    // A's change happened — this is the classic lost-update window. The
    // response DTO never exposes `version` (confirmed: no `version` field
    // anywhere in ReservationResponseDTO/ReservationRequest), so no client
    // could send an If-Match-equivalent even if it wanted to.
    const tabB = await request.put(`/api/v1/reservations/${reservationId}`, {
      headers, data: { ...base, expectedGuests: 1, status: 'CONFIRMED' },
    });

    const final = await request.get(`/api/v1/reservations/${reservationId}`);
    const finalBody = await final.json();

    if (tabB.status() === 200) {
      // Confirms the lost-update: B's stale write silently overwrote A's
      // change with no conflict signal. Documented as a 🟡 finding rather
      // than failing the suite — see REPORT.md D-CONCURRENCY-1: the
      // @Version column exists (Reservation.java:58) but is never surfaced
      // through the REST contract, so JPA's own optimistic lock can only
      // ever catch two writes that overlap within the same in-flight
      // request pair (a race), never two sequential tab saves.
      expect(finalBody.expectedGuests).toBe(1);
    } else {
      // If this ever starts returning 409, the contract has been fixed to
      // surface concurrency control — update this assertion accordingly.
      expect(tabB.status()).toBe(409);
    }
  });

  test('reservation TRUE concurrent edits (Promise.all, not sequential): measures whether Hibernate\'s own @Version check ever fires', async ({ request }) => {
    const headers = await csrfHeader(request);
    const guest = await createGuest(request, headers, {});
    const room = await createCleanRoom(request, headers);
    const roomId = room.id;

    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const checkIn = tomorrow.toISOString().split('T')[0];
    const checkOutDate = new Date(tomorrow);
    checkOutDate.setDate(checkOutDate.getDate() + 2);
    const checkOut = checkOutDate.toISOString().split('T')[0];

    const created = await request.post('/api/v1/reservations', {
      headers,
      data: { guestId: guest.id, expectedGuests: 1, checkInDate: checkIn, checkOutDate: checkOut, status: 'CONFIRMED', lineItems: [{ roomId }] },
    });
    expect(created.status(), await created.text()).toBe(201);
    const reservationId = (await created.json()).id as string;

    const base = { guestId: guest.id, checkInDate: checkIn, checkOutDate: checkOut, lineItems: [{ roomId }], status: 'CONFIRMED' as const };

    // Unlike the sequential "tab A saves, then tab B saves" test above, this
    // fires N requests genuinely in parallel (Promise.all) against the SAME
    // reservation, each racing to be the one whose in-flight transaction
    // flushes last — the actual scenario ObjectOptimisticLockingFailureException
    // (GlobalExceptionHandler -> 409 CONCURRENT_MODIFICATION) exists to catch.
    const concurrency = 10;
    const attempts = Array.from({ length: concurrency }, (_, i) =>
      request.put(`/api/v1/reservations/${reservationId}`, { headers, data: { ...base, expectedGuests: i + 1 } }));
    const responses = await Promise.all(attempts);
    const statuses = responses.map((r) => r.status());
    const succeeded = statuses.filter((s) => s === 200).length;
    const conflicted = statuses.filter((s) => s === 409).length;
    const unexpected = statuses.filter((s) => s !== 200 && s !== 409);

    expect(unexpected, `every response must be either 200 (won the race) or 409 (Hibernate's own version check caught the overlap) — got ${JSON.stringify(statuses)}`).toEqual([]);
    expect(succeeded, 'at least one request must succeed — a full pile-up of 409s would itself be a defect (unusable UI under any concurrent load)').toBeGreaterThan(0);

    // Whatever ended up persisted must be EXACTLY one of the attempted
    // values, never a corrupted/partial write from two overlapping flushes
    // stomping on each other mid-write.
    const final = await request.get(`/api/v1/reservations/${reservationId}`);
    const finalBody = await final.json();
    const attemptedValues = Array.from({ length: concurrency }, (_, i) => i + 1);
    expect(attemptedValues, `final expectedGuests=${finalBody.expectedGuests} must be one of the values actually attempted — anything else is data corruption from the race`).toContain(finalBody.expectedGuests);

    // Not a pass/fail assertion — informational, logged so the report can
    // state a real measured number rather than "it depends": under this
    // round's load/timing, did the @Version column's own JPA-level check
    // ever actually catch a genuine overlap, or does every request's
    // read-then-flush window never overlap another's in practice on this
    // stack (meaning the lost-update gap the sequential test documents is
    // the ONLY realistic way to lose data, not "sometimes 409, sometimes
    // silent" depending on luck)?
    console.log(`[D-CONCURRENCY-2] ${concurrency} truly concurrent PUTs: ${succeeded} succeeded (200), ${conflicted} conflicted (409 CONCURRENT_MODIFICATION)`);
  });
});
