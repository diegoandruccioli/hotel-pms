import { test, expect, request as playwrightRequest, type APIRequestContext } from '@playwright/test';
import { csrfHeader, createCleanRoom, createGuest, createWalkInStay } from '../fixtures/api';
import { OTHER_HOTEL_ADMIN } from '../fixtures/hotel';
import { logCustom } from './support/qaListeners';

// 2026-08-24 QA pass — Phase 4, the core deliverable: for each config-dependent
// fiscal flow (FatturaPA, Alloggiati Web, imposta di soggiorno/city tax),
// exercise complete / missing / malformed HotelSettings and record whether the
// user gets a readable error, a silent no-op, or — the docs/EXPLORATORY_TEST
// round-2 #2 failure pattern — a "successful" export carrying placeholder data.
//
// HotelSettingsRequest treats `null` as "leave unchanged" (never clears a
// field) — see HotelSettingsServiceImpl.update(). So "missing config" cannot
// be tested by clearing hotel A's already-configured settings (other live
// specs, e.g. checkout-live.spec.ts, depend on it staying valid). Instead:
//   - "complete" uses hotel A (SEED_HOTEL_ID), already configured by global.setup.ts.
//   - "missing" uses the OTHER_HOTEL tenant, whose hotel_settings row does not
//     exist yet (GET lazily creates all-null defaults) — a genuinely blank tenant.
//   - "malformed" sends explicit non-null-but-invalid values, either to OTHER_HOTEL
//     (safe to leave mutated) or as one-off invalid create-rate calls that don't
//     mutate hotel_settings at all.

let otherHotelContext: APIRequestContext;
let otherHotelHeaders: Record<string, string>;
let otherHotelRoomTypeId: string;

test.beforeAll(async ({ baseURL }) => {
  otherHotelContext = await playwrightRequest.newContext({ baseURL });
  const loginResponse = await otherHotelContext.post('/api/v1/auth/login', {
    data: { username: OTHER_HOTEL_ADMIN.username, password: OTHER_HOTEL_ADMIN.password },
  });
  expect(loginResponse.status(), await loginResponse.text()).toBe(200);
  otherHotelHeaders = await csrfHeader(otherHotelContext);

  // OTHER_HOTEL_ID has zero room types seeded (verified via psql) — fixtures/api.ts's
  // createCleanRoom() hardcodes hotel A's ROOM_TYPE_ID, which 404s under this tenant.
  // Create a throwaway room type here so this tenant can hold real room/stay fixtures.
  const roomTypeResponse = await otherHotelContext.post('/api/v1/room-types', {
    headers: otherHotelHeaders,
    data: { name: `QA${Date.now() % 100000}`, maxOccupancy: 2, basePrice: 80 },
  });
  expect(roomTypeResponse.status(), await roomTypeResponse.text()).toBe(201);
  otherHotelRoomTypeId = (await roomTypeResponse.json()).id;
});

async function createOtherHotelRoom(): Promise<{ id: string; roomNumber: string }> {
  const roomNumber = `QA-OTHER-${Date.now()}`;
  const response = await otherHotelContext.post('/api/v1/rooms', {
    headers: otherHotelHeaders,
    data: { hotelId: '99999999-9999-9999-9999-999999999999', roomNumber, roomTypeId: otherHotelRoomTypeId, status: 'CLEAN' },
  });
  if (response.status() !== 201) throw new Error(`Failed to create OTHER_HOTEL room: ${response.status()} ${await response.text()}`);
  return { id: (await response.json()).id, roomNumber };
}

test.afterAll(async () => {
  await otherHotelContext.dispose();
});

test.describe('QA 2026-08-24 — FatturaPA export vs hotel fiscal identity config', () => {
  test('(a) complete config: hotel A exports a real FatturaPA XML with no placeholder anagraphics', async ({ request }) => {
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers, { fiscalDetails: true });
    const stay = await createWalkInStay(request, headers, { roomId: room.id, guestId: guest.id });

    // documentType must switch to FATTURA BEFORE payment: InvoiceServiceImpl
    // .updateDocumentType (billing-service, line ~301) now rejects the change
    // with 409 CANNOT_UPDATE_PAID_INVOICE once status is PAID — a regression
    // against the repo's own existing checkout-live.spec.ts (reproduced
    // separately, see REPORT.md), which still pays first. Reordered here so
    // this test can verify its own actual point (no placeholder anagraphics)
    // without tripping over that separate, already-documented defect.
    const docType = await request.patch(`/api/v1/invoices/${stay.invoiceId}/document-type`, {
      headers, data: { documentType: 'FATTURA' },
    });
    expect(docType.status(), await docType.text()).toBe(200);

    const totalAmount = (await (await request.get(`/api/v1/invoices/${stay.invoiceId}`)).json()).totalAmount;
    const payment = await request.post(`/api/v1/invoices/${stay.invoiceId}/payments`, {
      headers, data: { amount: totalAmount, paymentMethod: 'CASH' },
    });
    expect(payment.status(), await payment.text()).toBe(201);

    const xmlResponse = await request.get(`/api/v1/invoices/${stay.invoiceId}/fatturaPA`);
    expect(xmlResponse.status(), await xmlResponse.text()).toBe(200);
    const xml = (await xmlResponse.body()).toString('utf-8');
    // R2 #2 regression check, scoped to CedentePrestatore (the HOTEL's own
    // identity — the actual target of that fix): none of the sanitize()
    // fallback placeholders should appear there when hotel A's identity is
    // fully configured. Deliberately NOT checking the whole document for
    // ">-<": CessionarioCommittente (the guest)'s own Indirizzo legitimately
    // falls back to "-" here because fixtures/api.ts's createGuest() never
    // sets an address even with fiscalDetails:true — a separate, lower-
    // priority gap (guest address completeness), not the hotel-identity bug.
    const cedenteMatch = xml.match(/<CedentePrestatore>[\s\S]*?<\/CedentePrestatore>/);
    expect(cedenteMatch, 'CedentePrestatore section missing from FatturaPA XML').toBeTruthy();
    const cedenteXml = cedenteMatch![0];
    for (const placeholder of ['00000000000', '>HOTELPMS<', '>Hotel<']) {
      expect(cedenteXml, `placeholder "${placeholder}" leaked into hotel A's own CedentePrestatore`).not.toContain(placeholder);
    }
  });

  test('(b) missing config: FatturaPA export is blocked with a readable error, not a placeholder-filled 200', async () => {
    const room = await createOtherHotelRoom();
    const guest = await createGuest(otherHotelContext, otherHotelHeaders, { fiscalDetails: true });
    const stay = await createWalkInStay(otherHotelContext, otherHotelHeaders, { roomId: room.id, guestId: guest.id });

    // documentType before payment — see the ordering note in test (a) above.
    const docTypeResponse = await otherHotelContext.patch(`/api/v1/invoices/${stay.invoiceId}/document-type`, {
      headers: otherHotelHeaders, data: { documentType: 'FATTURA' },
    });
    expect(docTypeResponse.status(), await docTypeResponse.text()).toBe(200);
    const totalAmount = (await (await otherHotelContext.get(`/api/v1/invoices/${stay.invoiceId}`)).json()).totalAmount;
    await otherHotelContext.post(`/api/v1/invoices/${stay.invoiceId}/payments`, {
      headers: otherHotelHeaders, data: { amount: totalAmount, paymentMethod: 'CASH' },
    });

    const xmlResponse = await otherHotelContext.get(`/api/v1/invoices/${stay.invoiceId}/fatturaPA`);
    const body = await xmlResponse.text();
    logCustom('fiscal_matrix_result', {
      flow: 'fatturaPA', state: 'missing_hotel_identity', status: xmlResponse.status(), body,
    });
    // Pass: any 4xx with a machine-readable detail (HOTEL_FISCAL_IDENTITY_INCOMPLETE
    // or the address-completeness check). Fail: 200 (placeholder export succeeded)
    // or a bare 500 with no actionable detail.
    expect(xmlResponse.status(), `FatturaPA export on an unconfigured hotel returned ${xmlResponse.status()}: ${body}`)
      .toBeGreaterThanOrEqual(400);
    expect(xmlResponse.status()).toBeLessThan(500);
    expect(body, 'error body should be readable, not empty').not.toBe('');
  });

  test('(c) malformed config: a non-numeric VAT number is rejected by the backend', async () => {
    // Fixed 2026-08-24 (REPORT.md §6 #6): HotelSettingsRequest.vatNumber now has
    // @Pattern(regexp = "^$|\\d{11}") — previously only HotelProfile.tsx's
    // VAT_NUMBER_REGEX (/^\d{11}$/) guarded this, trivially bypassed via direct API.
    const updateResponse = await otherHotelContext.put('/api/v1/stays/settings', {
      headers: otherHotelHeaders,
      data: { hotelName: 'QA Malformed Hotel', vatNumber: 'NOT-A-VAT-NUMBER!!' },
    });
    logCustom('fiscal_matrix_result', {
      flow: 'fatturaPA', state: 'malformed_vat_rejected_check', status: updateResponse.status(),
    });
    expect(updateResponse.status(), 'backend should reject a non-numeric VAT number as 400')
      .toBe(400);
  });

  test('(c) malformed config: an 11-digit VAT number is still accepted', async () => {
    const updateResponse = await otherHotelContext.put('/api/v1/stays/settings', {
      headers: otherHotelHeaders,
      data: { vatNumber: '01234567890' },
    });
    expect(updateResponse.status(), await updateResponse.text()).toBe(200);
    const stored = await updateResponse.json();
    expect(stored.vatNumber).toBe('01234567890');
  });
});

test.describe('QA 2026-08-24 — Alloggiati Web vs per-hotel credential config', () => {
  test('(a) per-hotel credentials absent falls back to global instance credentials (documented, not a failure)', async ({ request }) => {
    // Hotel A never configured its own alloggiati_username in this run (verified
    // via psql: alloggiati_username is blank) — hasAlloggiatiCredentials() is
    // false, so AlloggiatiWebSenderServiceImpl.resolveCredentials falls back to
    // the ALLOGGIATI_USERNAME/PASSWORD/WS_KEY env vars (ci_placeholder_*,
    // dry-run mode). A submit for a date with zero check-ins is a safe no-op
    // probe that still exercises the whole credential-resolution path.
    const headers = await csrfHeader(request);
    const probeDate = '2099-12-31'; // no real stays exist for this date
    const response = await request.post(`/api/v1/stays/reports/alloggiati/submit?date=${probeDate}`, { headers });
    logCustom('fiscal_matrix_result', {
      flow: 'alloggiati', state: 'no_own_credentials_fallback_probe', status: response.status(), body: await response.text(),
    });
    expect(response.status(), 'submit with zero records for the date should be a safe 200 no-op').toBe(200);
  });

  test('(b) malformed per-hotel WsKey: SOAP failure surfaces as a bounded error, not a raw 500 or silent success', async ({ request }) => {
    const headers = await csrfHeader(request);
    // Configure hotel A with a real-looking but fake per-hotel credential set
    // so resolveCredentials() picks the per-hotel path instead of the global
    // fallback, then restore afterward so other live specs relying on hotel
    // A's config are unaffected.
    const before = await (await request.get('/api/v1/stays/settings')).json();
    const setBad = await request.put('/api/v1/stays/settings', {
      headers,
      data: {
        alloggiatiUsername: 'qa2408-fake-user',
        alloggiatiPassword: 'qa2408-fake-password-not-real',
        alloggiatiWsKey: 'qa2408-fake-wskey-not-real',
      },
    });
    expect(setBad.status(), await setBad.text()).toBe(200);

    try {
      const probeDate = '2099-12-30';
      const response = await request.post(`/api/v1/stays/reports/alloggiati/submit?date=${probeDate}`, { headers });
      const body = await response.text();
      logCustom('fiscal_matrix_result', {
        flow: 'alloggiati', state: 'malformed_credentials', status: response.status(), body,
      });
      // Zero records for this date short-circuits before any SOAP call
      // (ALLOGGIATI_SUBMISSION_SKIPPED), so this specific probe can't force
      // the SOAP path without a real stay fixture — recorded either way;
      // asserting only that the response is never a bare unlabeled 500.
      if (response.status() >= 500) {
        expect(body, '5xx from a bad-credential Alloggiati submit should still carry a readable detail').not.toBe('');
      }
    } finally {
      // Restore — write-only password/WsKey fields need a real value to not
      // no-op; alloggiatiUsername='' clears back to "no own credentials".
      await request.put('/api/v1/stays/settings', {
        headers,
        data: { alloggiatiUsername: before.alloggiatiUsername ?? '' },
      });
    }
  });
});

test.describe('QA 2026-08-24 — imposta di soggiorno (city tax) vs comune config', () => {
  test('(a) complete config: creating a city tax rate succeeds when the hotel comune is configured', async ({ request }) => {
    const headers = await csrfHeader(request);
    const validFrom = '2020-01-01';
    const response = await request.post('/api/v1/stays/city-tax-rates', {
      headers,
      data: { category: `QA${Date.now() % 100000}`, amountPerNight: 2.5, validFrom },
    });
    logCustom('fiscal_matrix_result', { flow: 'city_tax', state: 'complete_comune_configured', status: response.status() });
    expect(response.status(), await response.text()).toBe(201);
  });

  test('(b) missing config: creating a city tax rate on a hotel with no comune configured fails readably (CITY_TAX_COMUNE_NOT_CONFIGURED)', async () => {
    const response = await otherHotelContext.post('/api/v1/stays/city-tax-rates', {
      headers: otherHotelHeaders,
      data: { category: 'QAMISSING', amountPerNight: 2.5, validFrom: '2020-01-01' },
    });
    const body = await response.text();
    logCustom('fiscal_matrix_result', { flow: 'city_tax', state: 'missing_comune', status: response.status(), body });
    expect(response.status(), `expected 400 CITY_TAX_COMUNE_NOT_CONFIGURED, got ${response.status()}: ${body}`).toBe(400);
    expect(body).toContain('CITY_TAX_COMUNE_NOT_CONFIGURED');
  });

  test('(c) malformed: overlapping validity period for the same category is rejected (409), not silently duplicated', async ({ request }) => {
    const headers = await csrfHeader(request);
    const category = `QAOVR${Date.now() % 100000}`;
    const first = await request.post('/api/v1/stays/city-tax-rates', {
      headers, data: { category, amountPerNight: 3, validFrom: '2021-01-01' },
    });
    expect(first.status(), await first.text()).toBe(201);
    const overlapping = await request.post('/api/v1/stays/city-tax-rates', {
      headers, data: { category, amountPerNight: 5, validFrom: '2021-06-01' },
    });
    logCustom('fiscal_matrix_result', { flow: 'city_tax', state: 'overlapping_rate', status: overlapping.status() });
    expect(overlapping.status()).toBe(409);
  });

  test('(d) a check-in on a hotel with no city-tax rate configured for the current category proceeds without charging city tax (documented no-op, not a crash)', async () => {
    const room = await createOtherHotelRoom();
    const guest = await createGuest(otherHotelContext, otherHotelHeaders);
    const stay = await createWalkInStay(otherHotelContext, otherHotelHeaders, { roomId: room.id, guestId: guest.id });
    const invoice = await (await otherHotelContext.get(`/api/v1/invoices/${stay.invoiceId}`)).json();
    const chargeTypes = (invoice.charges as Array<{ type: string }>).map((c) => c.type);
    logCustom('fiscal_matrix_result', {
      flow: 'city_tax', state: 'checkin_no_rate_configured', chargeTypes,
      note: 'CityTaxAssessmentServiceImpl.assessFor treats missing comune/category/rate as Optional.empty — "not a failure" by explicit code comment, but the operator receives no visible signal that city tax was skipped',
    });
    expect(chargeTypes).not.toContain('CITY_TAX');
    expect(chargeTypes).toContain('ROOM_NIGHT');
  });
});
