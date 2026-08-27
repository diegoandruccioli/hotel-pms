import { test, expect } from '@playwright/test';
import { csrfHeader, createCleanRoom, createGuest } from '../fixtures/api';
import { stopService, startServiceAndWaitHealthy } from './support/faultInjector';

// Blocco 6 — resilience: guest-service and fb-service down, one at a time.
// Real docker stop/start, not a route mock. Always restarts the service in
// `finally`, even on failure. Unlike notification/billing-service (which have
// explicit Resilience4j fallbacks), guest-service's getGuestById has none by
// design (GuestClient.java javadoc) — ReservationServiceImpl#verifyGuestExists
// catches feign.FeignException explicitly and translates it to a clean 502
// EXTERNAL_SERVICE_ERROR instead. fb-service is a direct downstream the
// api-gateway proxies to, not a Feign client inside frontdesk-service, so
// stopping it exercises the gateway's own error handling for a fully
// unreachable microservice.
test.describe('Blocco 6 — guest-service and fb-service resilience', () => {
  test('reservation creation while guest-service is down: translated 502, not a raw 500; recovers once restarted', async ({ request }) => {
    test.setTimeout(150_000);
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    // Guest created BEFORE stopping guest-service, so its id is valid — this
    // isolates "guest-service unreachable" from "guest genuinely not found".
    const guest = await createGuest(request, headers, {});

    const inFiveMonths = new Date();
    inFiveMonths.setMonth(inFiveMonths.getMonth() + 5);
    const checkIn = inFiveMonths.toISOString().split('T')[0];
    const checkOutDate = new Date(inFiveMonths);
    checkOutDate.setDate(checkOutDate.getDate() + 1);
    const checkOut = checkOutDate.toISOString().split('T')[0];
    const payload = {
      guestId: guest.id, expectedGuests: 1, checkInDate: checkIn, checkOutDate: checkOut,
      status: 'CONFIRMED', lineItems: [{ roomId: room.id }],
    };

    let serviceStopped = false;
    try {
      stopService('guest-service');
      serviceStopped = true;

      const response = await request.post('/api/v1/reservations', { headers, data: payload });
      const text = await response.text();
      expect(response.status(), text).toBe(502);
      const body = JSON.parse(text);
      expect(body.detail, 'must be the translated EXTERNAL_SERVICE_ERROR code, not a raw stack trace or generic 500').toBe('EXTERNAL_SERVICE_ERROR');
      expect(text, 'the internal Docker service URL must never leak to the client (security-report.md Finding #17)').not.toContain('guest-service:8083');
    } finally {
      if (serviceStopped) {
        await startServiceAndWaitHealthy('guest-service', 90_000);
      }
    }

    // Once guest-service is back, the exact same request must succeed for real.
    const retryHeaders = await csrfHeader(request);
    const retryResponse = await request.post('/api/v1/reservations', { headers: retryHeaders, data: payload });
    expect(retryResponse.status(), await retryResponse.text()).toBe(201);
  });

  test('F&B order while fb-service is down: gateway returns a clean error, unrelated endpoints keep working (no cascade)', async ({ request }) => {
    test.setTimeout(150_000);

    let serviceStopped = false;
    try {
      stopService('fb-service');
      serviceStopped = true;

      const menuResponse = await request.get('/api/v1/fb/menu-items');
      expect([502, 503, 504], `expected a gateway-level unavailability status, got ${menuResponse.status()}`).toContain(menuResponse.status());
      const menuText = await menuResponse.text();
      expect(menuText.toLowerCase(), 'no raw Java stack trace should ever reach the client').not.toContain('exception');

      // The rest of the app must not cascade-fail just because F&B is down —
      // an unrelated, unrelated-service endpoint must respond normally.
      const guestsResponse = await request.get('/api/v1/guests/search?page=0&size=1');
      expect(guestsResponse.status()).toBe(200);
      const roomsResponse = await request.get(`/api/v1/rooms?roomTypeId=`);
      expect([200, 400]).toContain(roomsResponse.status()); // never 5xx from an unrelated call
    } finally {
      if (serviceStopped) {
        await startServiceAndWaitHealthy('fb-service', 90_000);
      }
    }

    const retryHeaders = await csrfHeader(request);
    const menuAfter = await request.get('/api/v1/fb/menu-items', { headers: retryHeaders });
    expect(menuAfter.status()).toBe(200);
  });
});
