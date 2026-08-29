import { test, expect } from '@playwright/test';
import { csrfHeader, createCleanRoom, createWalkInStay } from '../fixtures/api';

// Blocco 6/7 — real SMTP delivery, now that docker-compose.yml has a mailpit
// catch-all service (added specifically to close the "structurally
// impossible to verify" gap documented in REPORT.md: notification-service's
// SMTP_HOST pointed at a host that didn't exist in this stack). This drives
// a real checkout through the real backend and asserts against mailpit's own
// REST API (http://localhost:8025/api/v1) that the email actually arrived —
// not just that the API call to notification-service returned 200 — with
// the invoice PDF attached, since that's exactly what StayNotificationCoordinator
// is supposed to do (fetch the real invoice PDF from billing-service and
// attach it, per its javadoc).
const MAILPIT_URL = 'http://localhost:8025';

test.describe('Blocco 6/7 — real email delivery via mailpit', () => {
  test('checkout email actually lands in the inbox with the invoice PDF attached', async ({ request }) => {
    test.setTimeout(60_000);
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    const uniqueGuestEmail = `qa25.mailpit.${Date.now()}@example.com`;
    const guestResponse = await request.post('/api/v1/guests', {
      headers, data: { firstName: 'Live', lastName: 'Suite Guest', email: uniqueGuestEmail },
    });
    expect(guestResponse.status(), await guestResponse.text()).toBe(201);
    const guest = await guestResponse.json();

    const stay = await createWalkInStay(request, headers, { roomId: room.id, guestId: guest.id });
    const invoice = await request.get(`/api/v1/invoices/${stay.invoiceId}`);
    const { totalAmount } = (await invoice.json()) as { totalAmount: number };
    const payment = await request.post(`/api/v1/invoices/${stay.invoiceId}/payments`, {
      headers, data: { amount: totalAmount, paymentMethod: 'CASH' },
    });
    expect(payment.status()).toBe(201);

    // Clear mailpit's inbox of anything from earlier runs so we only look at
    // what THIS checkout produced.
    await request.delete(`${MAILPIT_URL}/api/v1/messages`);

    const checkout = await request.put(`/api/v1/stays/${stay.id}/check-out`, { headers });
    expect(checkout.status(), await checkout.text()).toBe(200);

    // The email send is fire-and-forget from the checkout response's
    // perspective (checkout must not block on it) — poll mailpit briefly
    // rather than assuming it already landed.
    let found: { ID: string } | undefined;
    for (let attempt = 0; attempt < 15 && !found; attempt++) {
      const searchResponse = await request.get(
        `${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:${uniqueGuestEmail}`)}`,
      );
      const searchBody = await searchResponse.json();
      found = (searchBody.messages ?? [])[0];
      if (!found) await new Promise((resolve) => setTimeout(resolve, 1000));
    }
    expect(found, `no email for ${uniqueGuestEmail} arrived in mailpit within 15s of checkout`).toBeTruthy();

    const messageResponse = await request.get(`${MAILPIT_URL}/api/v1/message/${found!.ID}`);
    expect(messageResponse.status()).toBe(200);
    const message = await messageResponse.json();

    expect(message.To?.[0]?.Address).toBe(uniqueGuestEmail);
    expect(message.Attachments?.length, 'checkout email must have the invoice PDF attached, per StayNotificationCoordinator').toBeGreaterThan(0);

    const attachment = message.Attachments[0];
    expect(attachment.ContentType).toBe('application/pdf');

    const attachmentResponse = await request.get(`${MAILPIT_URL}/api/v1/message/${found!.ID}/part/${attachment.PartID}`);
    expect(attachmentResponse.status()).toBe(200);
    const attachmentBytes = await attachmentResponse.body();
    expect(attachmentBytes.subarray(0, 4).toString('latin1'), 'the attachment must be a real PDF, not a placeholder or empty file').toBe('%PDF');
    expect(attachmentBytes.length).toBeGreaterThan(1000);
  });

  test('reservation-confirmed email lands in the inbox (no attachment expected — nothing to attach yet)', async ({ request }) => {
    const headers = await csrfHeader(request);
    const guestEmail = `qa25.mailpit.resv.${Date.now()}@example.com`;
    const guestResponse = await request.post('/api/v1/guests', {
      headers, data: { firstName: 'Live', lastName: 'Suite Guest', email: guestEmail },
    });
    const guest = await guestResponse.json();
    // A freshly created room, not a shared seed room — avoids colliding with
    // another fixture reservation from an earlier run of this same spec at
    // the same date offset (ROOM_UNAVAILABLE_DATES).
    const room = await createCleanRoom(request, headers);
    const roomId = room.id;

    const inSixMonths = new Date();
    inSixMonths.setMonth(inSixMonths.getMonth() + 6);
    const checkOutDate = new Date(inSixMonths);
    checkOutDate.setDate(checkOutDate.getDate() + 1);

    await request.delete(`${MAILPIT_URL}/api/v1/messages`);

    const reservationResponse = await request.post('/api/v1/reservations', {
      headers,
      data: {
        guestId: guest.id, expectedGuests: 1,
        checkInDate: inSixMonths.toISOString().split('T')[0],
        checkOutDate: checkOutDate.toISOString().split('T')[0],
        status: 'CONFIRMED', lineItems: [{ roomId }],
      },
    });
    expect(reservationResponse.status(), await reservationResponse.text()).toBe(201);

    let found: { ID: string } | undefined;
    for (let attempt = 0; attempt < 15 && !found; attempt++) {
      const searchResponse = await request.get(
        `${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:${guestEmail}`)}`,
      );
      const searchBody = await searchResponse.json();
      found = (searchBody.messages ?? [])[0];
      if (!found) await new Promise((resolve) => setTimeout(resolve, 1000));
    }
    expect(found, `no reservation-confirmed email for ${guestEmail} arrived in mailpit`).toBeTruthy();
  });
});
