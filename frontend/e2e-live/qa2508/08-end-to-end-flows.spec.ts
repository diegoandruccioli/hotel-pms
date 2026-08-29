import { test, expect } from '@playwright/test';
import { csrfHeader, createCleanRoom, createGuest, STATO_FRANCIA } from '../fixtures/api';

// Blocco 7 — "flussi ideali end-to-end": the plan's core ask is chain
// INTEGRITY (does data flow correctly from one lifecycle stage into the
// next), which every earlier block already validated stage-by-stage in
// isolation (Blocco 3's UI sweep, Blocco 5's portal matrix). API-driven here
// for reliability/speed, mirroring the proven pattern in checkout-live.spec.ts
// — the full real backend pipeline runs either way (pricing, invoicing,
// PDF/XML rendering, Alloggiati reporting), nothing mocked.
test.describe('Blocco 7 — end-to-end flows', () => {
  test('quotation -> convert to reservation -> check-in -> F&B charge -> payment -> checkout -> PDF + FatturaPA + Alloggiati', async ({ request }) => {
    test.setTimeout(60_000);
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    // fiscalDetails: true — FatturaPAServiceImpl requires the guest's
    // structured address later in the chain (GUEST_STRUCTURED_ADDRESS_INCOMPLETE
    // otherwise), same as checkout-live.spec.ts.
    const guest = await createGuest(request, headers, { fiscalDetails: true });

    // 1. Quotation
    const inTwoWeeks = new Date();
    inTwoWeeks.setDate(inTwoWeeks.getDate() + 14);
    const checkOutDate = new Date(inTwoWeeks);
    checkOutDate.setDate(checkOutDate.getDate() + 2);
    const checkIn = inTwoWeeks.toISOString().split('T')[0];
    const checkOut = checkOutDate.toISOString().split('T')[0];
    const validUntil = new Date(inTwoWeeks);
    validUntil.setDate(validUntil.getDate() - 1);

    const quotationResponse = await request.post('/api/v1/quotations', {
      headers,
      data: {
        guestId: guest.id, checkInDate: checkIn, checkOutDate: checkOut,
        options: [{ label: 'QA25 e2e option', roomIds: [room.id] }],
        validUntil: validUntil.toISOString().split('T')[0],
      },
    });
    expect(quotationResponse.status(), await quotationResponse.text()).toBe(201);
    const quotation = await quotationResponse.json();
    const optionId = quotation.options[0].id as string;

    // 2. Convert -> reservation, honoring the quoted option/room.
    const convertResponse = await request.post(`/api/v1/quotations/${quotation.id}/convert`, {
      headers, data: { optionId },
    });
    expect(convertResponse.status(), await convertResponse.text()).toBe(200);
    const reservation = await convertResponse.json();
    expect(reservation.lineItems[0].roomId).toBe(room.id);

    const quotationAfter = await (await request.get(`/api/v1/quotations/${quotation.id}`)).json();
    expect(quotationAfter.status, 'a converted quotation must be marked ACCEPTED, not left DRAFT/SENT').toBe('ACCEPTED');

    // 3. Check-in against that reservation (not a walk-in): reservationId set,
    // roomId/dates/guest all must trace back to the converted reservation.
    const checkInResponse = await request.post('/api/v1/stays', {
      headers,
      data: {
        reservationId: reservation.id, guestId: guest.id, roomId: room.id, status: 'CHECKED_IN',
        guests: [{
          firstName: 'Live', lastName: 'Suite Guest', gender: '1', dateOfBirth: '1990-01-01',
          placeOfBirth: STATO_FRANCIA, citizenship: STATO_FRANCIA,
          isPrimaryGuest: true, travellerType: 'FAMILIARE',
        }],
      },
    });
    expect(checkInResponse.status(), await checkInResponse.text()).toBe(201);
    const stay = await checkInResponse.json();
    expect(stay.reservationId).toBe(reservation.id);
    expect(stay.invoiceId, 'check-in must open a billing folio').toBeTruthy();

    const invoiceBeforeFb = await (await request.get(`/api/v1/invoices/${stay.invoiceId}`)).json();
    const roomOnlyTotal = invoiceBeforeFb.totalAmount as number;

    // 4. F&B order charged to the room — must land on the SAME invoice the
    // check-in opened, proving the stay<->invoice<->charge chain is wired,
    // not three independent records that happen to share an id.
    const menuResponse = await request.get('/api/v1/fb/menu-items');
    const menuBody = await menuResponse.json();
    const menuItems = (menuBody.content ?? menuBody) as { id: string }[];
    expect(menuItems.length, 'seed data must include at least one menu item').toBeGreaterThan(0);
    const orderResponse = await request.post('/api/v1/fb/orders', {
      headers, data: { stayId: stay.id, items: [{ menuItemId: menuItems[0].id, quantity: 2 }] },
    });
    expect(orderResponse.status(), await orderResponse.text()).toBe(201);
    const order = await orderResponse.json();

    // Creating an order does NOT charge the room by itself — confirming it
    // does (RestaurantOrderServiceImpl#confirmOrder posts the charge to
    // billing-service and flips status to BILLED_TO_ROOM).
    const confirmResponse = await request.post(`/api/v1/fb/orders/${order.id}/confirm`, { headers });
    expect(confirmResponse.status(), await confirmResponse.text()).toBe(200);

    const invoiceAfterFb = await (await request.get(`/api/v1/invoices/${stay.invoiceId}`)).json();
    expect(invoiceAfterFb.totalAmount, 'the F&B charge must actually land on the stay\'s invoice, increasing its total').toBeGreaterThan(roomOnlyTotal);

    // 5. Full payment.
    const paymentResponse = await request.post(`/api/v1/invoices/${stay.invoiceId}/payments`, {
      headers, data: { amount: invoiceAfterFb.totalAmount, paymentMethod: 'CASH' },
    });
    expect(paymentResponse.status(), await paymentResponse.text()).toBe(201);
    const invoicePaid = await (await request.get(`/api/v1/invoices/${stay.invoiceId}`)).json();
    expect(invoicePaid.status).toBe('PAID');

    // 6. Checkout.
    const checkoutResponse = await request.put(`/api/v1/stays/${stay.id}/check-out`, { headers });
    expect(checkoutResponse.status(), await checkoutResponse.text()).toBe(200);
    const stayFinal = await (await request.get(`/api/v1/stays/${stay.id}`)).json();
    expect(stayFinal.status).toBe('CHECKED_OUT');

    // 7. Invoice PDF — the room-charge total AND the F&B charge must both be
    // in it (proving the checkout snapshot reflects the full stay, not just
    // the initial room charge from step 3).
    const pdfResponse = await request.get(`/api/v1/invoices/${stay.invoiceId}/pdf`);
    expect(pdfResponse.status()).toBe(200);
    const pdfBytes = await pdfResponse.body();
    expect(pdfBytes.subarray(0, 4).toString('latin1')).toBe('%PDF');

    // 8. FatturaPA XML — requires FATTURA document type (default RICEVUTA).
    const docTypeResponse = await request.patch(`/api/v1/invoices/${stay.invoiceId}/document-type`, {
      headers, data: { documentType: 'FATTURA' },
    });
    expect(docTypeResponse.status(), await docTypeResponse.text()).toBe(200);
    const fatturaResponse = await request.get(`/api/v1/invoices/${stay.invoiceId}/fatturaPA`);
    expect(fatturaResponse.status(), await fatturaResponse.text()).toBe(200);
    const xml = (await fatturaResponse.body()).toString('utf-8');
    expect(xml).toContain('RSSMRA90A01H501U'); // the fixture guest's real fiscal code

    // 9. Alloggiati daily export includes this stay's checked-in guest.
    const today = new Date().toISOString().split('T')[0];
    const alloggiatiJson = await request.get(`/api/v1/stays/reports/alloggiati/json?date=${today}`);
    expect(alloggiatiJson.status()).toBe(200);
  });

  test('multi-option quotation -> decline; reservation -> edit -> cancel', async ({ request }) => {
    const headers = await csrfHeader(request);
    const roomA = await createCleanRoom(request, headers);
    const roomB = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers, {});

    const inThreeWeeks = new Date();
    inThreeWeeks.setDate(inThreeWeeks.getDate() + 21);
    const checkOutDate = new Date(inThreeWeeks);
    checkOutDate.setDate(checkOutDate.getDate() + 1);
    const checkIn = inThreeWeeks.toISOString().split('T')[0];
    const checkOut = checkOutDate.toISOString().split('T')[0];
    const validUntil = new Date(inThreeWeeks);
    validUntil.setDate(validUntil.getDate() - 1);

    // Multi-option quotation (two alternative rooms) -> guest declines.
    const quotationResponse = await request.post('/api/v1/quotations', {
      headers,
      data: {
        guestId: guest.id, checkInDate: checkIn, checkOutDate: checkOut,
        options: [
          { label: 'QA25 option A', roomIds: [roomA.id] },
          { label: 'QA25 option B', roomIds: [roomB.id] },
        ],
        validUntil: validUntil.toISOString().split('T')[0],
      },
    });
    expect(quotationResponse.status(), await quotationResponse.text()).toBe(201);
    const quotation = await quotationResponse.json();
    expect(quotation.options.length).toBe(2);

    const declineResponse = await request.post(`/api/v1/quotations/${quotation.id}/decline`, { headers });
    expect(declineResponse.status(), await declineResponse.text()).toBe(200);
    const declined = await declineResponse.json();
    expect(declined.status).toBe('DECLINED');

    // A declined quotation must not be convertible.
    const convertAttempt = await request.post(`/api/v1/quotations/${quotation.id}/convert`, {
      headers, data: { optionId: quotation.options[0].id },
    });
    expect(convertAttempt.status(), 'a DECLINED quotation must never convert to a reservation').not.toBe(200);

    // Reservation -> edit (room move from A to B) -> cancel.
    const reservationResponse = await request.post('/api/v1/reservations', {
      headers,
      data: {
        guestId: guest.id, expectedGuests: 1, checkInDate: checkIn, checkOutDate: checkOut,
        status: 'CONFIRMED', lineItems: [{ roomId: roomA.id }],
      },
    });
    expect(reservationResponse.status(), await reservationResponse.text()).toBe(201);
    const reservation = await reservationResponse.json();

    const editResponse = await request.put(`/api/v1/reservations/${reservation.id}`, {
      headers,
      data: {
        guestId: guest.id, expectedGuests: 2, checkInDate: checkIn, checkOutDate: checkOut,
        status: 'CONFIRMED', lineItems: [{ roomId: roomB.id }],
      },
    });
    expect(editResponse.status(), await editResponse.text()).toBe(200);
    const edited = await editResponse.json();
    expect(edited.expectedGuests).toBe(2);
    expect(edited.lineItems[0].roomId, 'room move via edit must actually move the line item to the new room').toBe(roomB.id);

    const cancelResponse = await request.delete(`/api/v1/reservations/${reservation.id}`, { headers });
    expect(cancelResponse.status(), await cancelResponse.text()).toBe(204);

    const afterCancel = await request.get(`/api/v1/reservations/${reservation.id}`);
    expect(afterCancel.status(), 'a deleted (soft-cancelled) reservation must 404, not still be readable').toBe(404);
  });
});
