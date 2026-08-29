import { test, expect } from '@playwright/test';
import { stopService, startServiceAndWaitHealthy } from './support/faultInjector';

// Blocco 6 — resilience: frontdesk-service down. Real docker stop/start, not
// a route mock. frontdesk-service is the central service (reservations,
// quotations, stays/check-in/checkout, rooms, rates, city-tax) — stopping it
// IS expected to degrade most of the app, by definition, not a bug to fix.
// What this test actually verifies: (1) the degradation is clean — api-
// gateway's GlobalErrorWebExceptionHandler translates the outage to a
// consistent 502, never a raw crash — and (2) genuinely unrelated services
// (guest-service, auth-service) keep working normally, proving there's no
// blast-radius cascade beyond frontdesk-service's own routes. Always
// restarts the service in `finally`, even on failure.
test.describe('Blocco 6 — frontdesk-service resilience', () => {
  test('frontdesk-service down degrades cleanly (502, not a crash); unrelated services keep working, no cascade', async ({ request }) => {
    test.setTimeout(150_000);

    let serviceStopped = false;
    try {
      stopService('frontdesk-service');
      serviceStopped = true;

      // 1. Direct frontdesk-service routes fail cleanly via the gateway's own
      // translation layer — same fix verified for fb-service in
      // 06-guest-fb-service-down.spec.ts, now confirmed for the central
      // service too.
      const reservationsResponse = await request.get('/api/v1/reservations?page=0&size=1');
      expect([502, 503, 504], `expected a clean gateway-level status, got ${reservationsResponse.status()}`).toContain(reservationsResponse.status());
      const reservationsText = await reservationsResponse.text();
      expect(reservationsText.toLowerCase(), 'no raw stack trace should ever reach the client').not.toContain('exception');

      const roomsResponse = await request.get('/api/v1/rooms?page=0&size=1');
      expect([502, 503, 504]).toContain(roomsResponse.status());

      // 2. Genuinely unrelated services must keep working — no cascade.
      const guestsResponse = await request.get('/api/v1/guests/search?page=0&size=1');
      expect(guestsResponse.status(), 'guest-service must be unaffected by frontdesk-service being down').toBe(200);

      const meResponse = await request.get('/api/v1/auth/me');
      expect(meResponse.status(), 'auth-service (session check) must be unaffected by frontdesk-service being down').toBe(200);
    } finally {
      if (serviceStopped) {
        await startServiceAndWaitHealthy('frontdesk-service', 90_000);
      }
    }

    // 3. Once restarted, frontdesk-service routes work for real again.
    const retryResponse = await request.get('/api/v1/reservations?page=0&size=1');
    expect(retryResponse.status(), await retryResponse.text()).toBe(200);
  });
});
