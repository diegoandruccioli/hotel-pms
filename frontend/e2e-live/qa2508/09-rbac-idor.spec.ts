import { test, expect, request as playwrightRequest, type APIRequestContext } from '@playwright/test';
import { csrfHeader, createCleanRoom, createGuest, createWalkInStay } from '../fixtures/api';
import { OTHER_HOTEL_ADMIN } from '../fixtures/hotel';
import { QA25_RECEPTIONIST } from './support/roles';

// Blocco 9 — RBAC (role-gated endpoints) and cross-tenant IDOR, extending
// e2e-live/idor-cross-tenant-live.spec.ts (which already covers room/guest/
// invoice IDOR and one RBAC 403 case) with the entities/actions that file
// doesn't touch: reservation and quotation IDOR, and the specific
// ADMIN/OWNER-only endpoints (Alloggiati, FatturaPA export, city-tax rate
// creation, hotel-category creation) a RECEPTIONIST must never reach.
test.describe('Blocco 9 — RBAC and cross-tenant IDOR (extended)', () => {
  let hotelAReservationId: string;
  let hotelAQuotationId: string;
  let hotelAStayId: string;
  let otherHotelContext: APIRequestContext;
  let receptionistContext: APIRequestContext;

  test.beforeAll(async ({ request, baseURL }) => {
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers, {});
    const stay = await createWalkInStay(request, headers, { roomId: room.id, guestId: guest.id });
    hotelAStayId = stay.id;

    const room2 = await createCleanRoom(request, headers);
    const inTwoMonths = new Date();
    inTwoMonths.setMonth(inTwoMonths.getMonth() + 2);
    const checkOutDate = new Date(inTwoMonths);
    checkOutDate.setDate(checkOutDate.getDate() + 1);
    const checkIn = inTwoMonths.toISOString().split('T')[0];
    const checkOut = checkOutDate.toISOString().split('T')[0];

    const reservationResponse = await request.post('/api/v1/reservations', {
      headers,
      data: { guestId: guest.id, expectedGuests: 1, checkInDate: checkIn, checkOutDate: checkOut, status: 'CONFIRMED', lineItems: [{ roomId: room2.id }] },
    });
    expect(reservationResponse.status(), await reservationResponse.text()).toBe(201);
    hotelAReservationId = (await reservationResponse.json()).id;

    const validUntil = new Date(inTwoMonths);
    validUntil.setDate(validUntil.getDate() - 1);
    const quotationResponse = await request.post('/api/v1/quotations', {
      headers,
      data: {
        guestId: guest.id, checkInDate: checkIn, checkOutDate: checkOut,
        options: [{ label: 'QA25 idor option', roomIds: [room2.id] }],
        validUntil: validUntil.toISOString().split('T')[0],
      },
    });
    expect(quotationResponse.status(), await quotationResponse.text()).toBe(201);
    hotelAQuotationId = (await quotationResponse.json()).id;

    otherHotelContext = await playwrightRequest.newContext({ baseURL });
    const otherLogin = await otherHotelContext.post('/api/v1/auth/login', {
      data: { username: OTHER_HOTEL_ADMIN.username, password: OTHER_HOTEL_ADMIN.password },
    });
    expect(otherLogin.status(), await otherLogin.text()).toBe(200);

    receptionistContext = await playwrightRequest.newContext({ baseURL });
    const receptLogin = await receptionistContext.post('/api/v1/auth/login', {
      data: { username: QA25_RECEPTIONIST.username, password: QA25_RECEPTIONIST.password },
    });
    expect(receptLogin.status(), await receptLogin.text()).toBe(200);
  });

  test.afterAll(async () => {
    await otherHotelContext.dispose();
    await receptionistContext.dispose();
  });

  test('IDOR: reservation, quotation and stay created for Hotel A are invisible (404) to Hotel B', async () => {
    const reservationResponse = await otherHotelContext.get(`/api/v1/reservations/${hotelAReservationId}`);
    expect(reservationResponse.status()).toBe(404);

    const quotationResponse = await otherHotelContext.get(`/api/v1/quotations/${hotelAQuotationId}`);
    expect(quotationResponse.status()).toBe(404);

    const stayResponse = await otherHotelContext.get(`/api/v1/stays/${hotelAStayId}`);
    expect(stayResponse.status()).toBe(404);
  });

  test('IDOR: Hotel B cannot check out Hotel A\'s stay it cannot see', async () => {
    const otherCsrf = (await otherHotelContext.storageState()).cookies.find((c) => c.name === 'csrf_token');
    expect(otherCsrf).toBeTruthy();
    const response = await otherHotelContext.put(`/api/v1/stays/${hotelAStayId}/check-out`, {
      headers: { 'X-CSRF-Token': otherCsrf!.value },
    });
    expect(response.status()).toBe(404);
  });

  test('RBAC: RECEPTIONIST is rejected (403) from every ADMIN/OWNER-only endpoint', async () => {
    const today = new Date().toISOString().split('T')[0];

    const alloggiatiTxt = await receptionistContext.get(`/api/v1/stays/reports/alloggiati?date=${today}`);
    expect(alloggiatiTxt.status(), 'Alloggiati .txt download must be ADMIN/OWNER-only').toBe(403);

    const alloggiatiJson = await receptionistContext.get(`/api/v1/stays/reports/alloggiati/json?date=${today}`);
    expect(alloggiatiJson.status(), 'Alloggiati .json download must be ADMIN/OWNER-only').toBe(403);

    const receptCsrf = (await receptionistContext.storageState()).cookies.find((c) => c.name === 'csrf_token');
    expect(receptCsrf).toBeTruthy();
    const submitHeaders = { 'X-CSRF-Token': receptCsrf!.value };

    const alloggiatiSubmit = await receptionistContext.post(`/api/v1/stays/reports/alloggiati/submit?date=${today}`, { headers: submitHeaders });
    expect(alloggiatiSubmit.status(), 'Alloggiati manual submit must be ADMIN/OWNER-only').toBe(403);

    const fatturaExport = await receptionistContext.get(`/api/v1/invoices/export?from=2000-01-01&to=2099-12-31&confirm=false`);
    expect(fatturaExport.status(), 'FatturaPA batch export must be ADMIN/OWNER-only').toBe(403);

    const cityTaxCreate = await receptionistContext.post('/api/v1/stays/city-tax-rates', {
      headers: submitHeaders,
      data: { comuneCodice: '999999', category: 'QA25', amountPerNight: 1, maxTaxableNights: 5, exemptUnderAge: 18, validFrom: today },
    });
    expect(cityTaxCreate.status(), 'city-tax rate creation must be ADMIN/OWNER-only').toBe(403);

    const hotelCategoryCreate = await receptionistContext.post('/api/v1/stays/hotel-category', {
      headers: submitHeaders,
      data: { category: 'QA25', validFrom: today },
    });
    expect(hotelCategoryCreate.status(), 'hotel-category creation must be ADMIN/OWNER-only').toBe(403);
  });

  test('RBAC: RECEPTIONIST CAN still read city-tax rates (GET is not gated, only POST)', async () => {
    const response = await receptionistContext.get('/api/v1/stays/city-tax-rates');
    expect(response.status(), 'reading city-tax rates must remain available to RECEPTIONIST').toBe(200);
  });
});
