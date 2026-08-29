import { test, expect } from '@playwright/test';
import { csrfHeader, createCleanRoom, createGuest, STATO_FRANCIA } from '../fixtures/api';
import { stopService, startServiceAndWaitHealthy } from './support/faultInjector';

// Blocco 6 — resilience: billing-service down during check-in.
// StayBillingCoordinator#openInvoiceForStay's @CircuitBreaker(CB_BILLING_SERVICE)
// fallback (BillingClient#createInvoiceForStayFallback) makes check-in record
// invoiceCreationFailed=true instead of failing the check-in itself
// (backup/DECISIONS.md §2.2) — mirrors the notification-service pattern in
// 06-notification-service-down.spec.ts. Real docker stop/start, not a route
// mock, so the actual Resilience4j circuit breaker fires. Always restarts the
// service in `finally`, even on failure.
test.describe('Blocco 6 — billing-service resilience', () => {
  test('check-in succeeds even while billing-service is down; retry recovers the invoice', async ({ request }) => {
    test.setTimeout(180_000);
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers, {});

    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);

    let serviceStopped = false;
    let stayId: string;
    try {
      stopService('billing-service');
      serviceStopped = true;

      const checkInResponse = await request.post('/api/v1/stays', {
        headers,
        data: {
          guestId: guest.id,
          roomId: room.id,
          status: 'CHECKED_IN',
          expectedCheckOutDate: tomorrow.toISOString().split('T')[0],
          guests: [{
            firstName: 'Live', lastName: 'Suite Guest', gender: '1', dateOfBirth: '1990-01-01',
            placeOfBirth: STATO_FRANCIA, citizenship: STATO_FRANCIA,
            isPrimaryGuest: true, travellerType: 'FAMILIARE',
          }],
        },
      });
      expect(checkInResponse.status(), 'check-in must succeed (201) even when billing-service is unreachable — the circuit breaker fallback must not fail the request').toBe(201);
      const body = await checkInResponse.json();
      stayId = body.id;
      expect(body.invoiceId, 'no invoice could be created while billing-service is down').toBeFalsy();

      const stayAfter = await (await request.get(`/api/v1/stays/${stayId}`)).json();
      expect(stayAfter.status).toBe('CHECKED_IN');
      expect(stayAfter.invoiceCreationFailed, 'the invoice failure must be recorded for later retry, not silently dropped').toBe(true);
      expect(stayAfter.invoiceCreationFailureReason).toBeTruthy();
    } finally {
      if (serviceStopped) {
        await startServiceAndWaitHealthy('billing-service', 90_000);
      }
    }

    // waitDurationInOpenState (frontdesk-service.yml) before the circuit
    // breaker will let a real call through again.
    await new Promise((resolve) => setTimeout(resolve, 11_000));
    const retryHeaders = await csrfHeader(request);
    const retryResponse = await request.post(`/api/v1/stays/${stayId!}/invoice/retry`, { headers: retryHeaders });
    expect(retryResponse.status(), await retryResponse.text()).toBe(200);

    const stayFinal = await (await request.get(`/api/v1/stays/${stayId!}`)).json();
    expect(stayFinal.invoiceId, 'retry once billing-service is back up must actually create the invoice').toBeTruthy();
    expect(stayFinal.invoiceCreationFailed, 'a successful retry must clear the failure flag').toBe(false);
  });
});
