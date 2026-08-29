import { test, expect } from '@playwright/test';
import { stopService, startServiceAndWaitHealthy } from './support/faultInjector';

// Blocco 6 — resilience: auth-service down. Real docker stop/start, not a
// route mock. Unlike guest/billing/fb-service (accessed via frontdesk-
// service's Feign clients, or proxied directly), auth-service has a unique
// property worth verifying explicitly: AuthenticationFilter
// (api-gateway/src/main/java/com/hotelpms/gateway/filter/AuthenticationFilter.java)
// validates the JWT LOCALLY at the gateway (HMAC-signed, own key) — it never
// calls auth-service to validate an already-issued token. So an existing
// authenticated session should keep working normally even while auth-service
// is completely down; only auth-service's OWN endpoints (login, refresh,
// /me, logout, change-password) should fail while it's unreachable. Always
// restarts the service in `finally`, even on failure.
test.describe('Blocco 6 — auth-service resilience', () => {
  test('existing session keeps working while auth-service is down; only auth endpoints are affected', async ({ request }) => {
    test.setTimeout(150_000);

    // Baseline: the existing session (global.setup.ts's login) can read data
    // through a route that never touches auth-service after token issuance.
    const before = await request.get('/api/v1/rooms?page=0&size=1');
    expect(before.status()).toBe(200);

    let serviceStopped = false;
    try {
      stopService('auth-service');
      serviceStopped = true;

      // 1. The existing session must keep working — proves JWT validation is
      // local to the gateway, not a per-request call to auth-service.
      const duringOutage = await request.get('/api/v1/rooms?page=0&size=1');
      expect(duringOutage.status(), 'an already-authenticated request must not depend on auth-service being reachable').toBe(200);

      const reservationsDuringOutage = await request.get('/api/v1/reservations?page=0&size=1');
      expect(reservationsDuringOutage.status()).toBe(200);

      // 2. A fresh login attempt (genuinely needs auth-service) must fail
      // cleanly — translated by api-gateway's GlobalErrorWebExceptionHandler
      // (502 EXTERNAL_SERVICE_ERROR), never a raw 500 or a hang.
      const loginAttempt = await request.post('/api/v1/auth/login', {
        data: { username: 'e2e-live-admin', password: 'wrong-password-doesnt-matter' },
      });
      expect([502, 503, 504], `login must fail with a clean gateway-level status while auth-service is down, got ${loginAttempt.status()}`).toContain(loginAttempt.status());
      const loginBody = await loginAttempt.text();
      expect(loginBody.toLowerCase(), 'no raw Java/Node stack trace should ever reach the client').not.toContain('exception');

      // 3. /me (also proxied straight to auth-service) fails cleanly too.
      const meAttempt = await request.get('/api/v1/auth/me');
      expect([502, 503, 504], `/me must fail with a clean gateway-level status while auth-service is down, got ${meAttempt.status()}`).toContain(meAttempt.status());
    } finally {
      if (serviceStopped) {
        await startServiceAndWaitHealthy('auth-service', 90_000);
      }
    }

    // 4. Once restarted, a fresh login succeeds again for real.
    const retryLogin = await request.post('/api/v1/auth/login', {
      data: { username: 'e2e-live-admin', password: 'E2eLiveAdmin!2026#run' },
    });
    expect(retryLogin.status(), await retryLogin.text()).toBe(200);
  });
});
