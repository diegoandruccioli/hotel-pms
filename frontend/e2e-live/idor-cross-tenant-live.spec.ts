import { test, expect, request as playwrightRequest, type APIRequestContext } from '@playwright/test';
import { csrfHeader, createCleanRoom, createGuest, createWalkInStay } from './fixtures/api';

// IDOR / RBAC cross-tenant isolation against the real backend (Fase 7 /
// item 20). This is exactly the class of bug (T-GST-01, T-BILL-01, T-STAY-04,
// ...) THREAT_MODEL.md documents as found and fixed one hotel_id-scoping gap
// at a time — a mocked spec can't regress-test tenant isolation at all,
// since the mock never has two tenants' data to confuse in the first place.
// This spec creates a second real hotel identity and proves the real
// backend actually enforces the boundary, not an assumption about it.

const OTHER_HOTEL_ID = '99999999-9999-9999-9999-999999999999';

test.describe('Cross-tenant IDOR and RBAC against the real backend', () => {
    let hotelARoomId: string;
    let hotelAGuestId: string;
    let hotelAInvoiceId: string;
    let otherHotelContext: APIRequestContext;

    test.beforeAll(async ({ request, baseURL }) => {
        // Hotel A fixtures, created with the shared live-suite admin identity
        // (global.setup.ts's storageState, already applied to `request`).
        const headers = await csrfHeader(request);
        const room = await createCleanRoom(request, headers);
        hotelARoomId = room.id;
        const guest = await createGuest(request, headers);
        hotelAGuestId = guest.id;
        const stay = await createWalkInStay(request, headers, { roomId: room.id, guestId: guest.id });
        hotelAInvoiceId = stay.invoiceId;

        // A second, independent identity for a DIFFERENT hotel — its own
        // browser-less API context, deliberately not sharing the "live"
        // project's storageState (that's Hotel A's session).
        otherHotelContext = await playwrightRequest.newContext({ baseURL });
        const username = `e2e-live-other-hotel-${Date.now()}`;
        const password = 'OtherHotelAdmin!2026#run';
        const registerResponse = await otherHotelContext.post('/api/v1/auth/register', {
            data: { username, password, email: `${username}@hotel-pms.local`, role: 'ADMIN', hotelId: OTHER_HOTEL_ID },
        });
        expect(registerResponse.status(), await registerResponse.text()).toBe(201);
        const loginResponse = await otherHotelContext.post('/api/v1/auth/login', { data: { username, password } });
        expect(loginResponse.status(), await loginResponse.text()).toBe(200);
    });

    test.afterAll(async () => {
        await otherHotelContext.dispose();
    });

    test('a room created for Hotel A is invisible (404, not 403 — no enumeration) to Hotel B', async () => {
        const response = await otherHotelContext.get(`/api/v1/rooms/${hotelARoomId}`);
        expect(response.status()).toBe(404);
    });

    test("Hotel A's room does not appear in Hotel B's room list", async () => {
        const response = await otherHotelContext.get('/api/v1/rooms?page=0&size=200');
        expect(response.status()).toBe(200);
        const rooms = (await response.json()).content as Array<{ id: string }>;
        expect(rooms.some((r) => r.id === hotelARoomId)).toBe(false);
    });

    test('a guest created for Hotel A is invisible to Hotel B', async () => {
        const response = await otherHotelContext.get(`/api/v1/guests/${hotelAGuestId}`);
        expect(response.status()).toBe(404);
    });

    test("Hotel A's invoice is invisible to Hotel B", async () => {
        const response = await otherHotelContext.get(`/api/v1/invoices/${hotelAInvoiceId}`);
        expect(response.status()).toBe(404);
    });

    test('Hotel B cannot pay a charge onto an invoice it cannot see', async () => {
        const otherHotelCsrf = (await otherHotelContext.storageState()).cookies.find((c) => c.name === 'csrf_token');
        expect(otherHotelCsrf, 'csrf_token missing for the Hotel B session').toBeTruthy();
        const response = await otherHotelContext.post(`/api/v1/invoices/${hotelAInvoiceId}/payments`, {
            headers: { 'X-CSRF-Token': otherHotelCsrf!.value },
            data: { amount: 1, paymentMethod: 'CASH' },
        });
        expect(response.status()).toBe(404);
    });

    test('RECEPTIONIST role is rejected from the OWNER/ADMIN-only financial report endpoint', async ({ baseURL }) => {
        const username = `e2e-live-receptionist-${Date.now()}`;
        const password = 'ReceptionistUser!2026#run';
        const receptionistContext = await playwrightRequest.newContext({ baseURL });
        try {
            const registerResponse = await receptionistContext.post('/api/v1/auth/register', {
                data: {
                    username, password, email: `${username}@hotel-pms.local`,
                    role: 'RECEPTIONIST', hotelId: OTHER_HOTEL_ID,
                },
            });
            expect(registerResponse.status(), await registerResponse.text()).toBe(201);
            const loginResponse = await receptionistContext.post('/api/v1/auth/login', { data: { username, password } });
            expect(loginResponse.status()).toBe(200);

            const reportResponse = await receptionistContext.get(
                '/api/v1/reports/owner?startDate=2000-01-01&endDate=2099-12-31',
            );
            expect(reportResponse.status()).toBe(403);
        } finally {
            await receptionistContext.dispose();
        }
    });
});
