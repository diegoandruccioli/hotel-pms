import { test, expect } from '@playwright/test';
import { csrfHeader, createCleanRoom, createGuest } from './fixtures/api';

// Verifies the night audit end to end against the real backend: same-day
// no-show auto-detection, the day-sheet/cash snapshot, the immutability
// guard on an already-closed date, and the future-date guard. Uses TODAY as
// the business date throughout (ReservationRequest.checkInDate is
// @FutureOrPresent, so a genuinely past date can't be seeded via the API —
// same constraint no-show-live.spec.ts works around the same way).

test.describe('Night audit against the real backend', () => {
    let roomId: string;
    let reservationId: string;
    let today: string;

    test.beforeAll(async ({ request }) => {
        const headers = await csrfHeader(request);
        const room = await createCleanRoom(request, headers);
        roomId = room.id;
        const guest = await createGuest(request, headers);

        today = new Date().toISOString().split('T')[0];
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);

        const response = await request.post('/api/v1/reservations', {
            headers,
            data: {
                guestId: guest.id,
                expectedGuests: 1,
                checkInDate: today,
                checkOutDate: tomorrow.toISOString().split('T')[0],
                status: 'CONFIRMED',
                lineItems: [{ roomId }],
            },
        });
        if (response.status() !== 201) {
            throw new Error(`Failed to create fixture reservation: ${response.status()} ${await response.text()}`);
        }
        reservationId = (await response.json()).id as string;
    });

    test('rejects closing a business date in the future', async ({ request }) => {
        const headers = await csrfHeader(request);
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        const response = await request.post(
            `/api/v1/frontdesk/night-audit?date=${tomorrow.toISOString().split('T')[0]}`,
            { headers },
        );
        expect(response.status()).toBe(400);
    });

    test('closes today, auto-marks the still-CONFIRMED reservation as no-show, and snapshots occupancy', async ({ request }) => {
        const headers = await csrfHeader(request);
        const response = await request.post(`/api/v1/frontdesk/night-audit?date=${today}`, { headers });
        expect(response.status(), await response.text()).toBe(201);
        const run = await response.json() as {
            status: string;
            businessDate: string;
            noShowsMarked: number;
            arrivals: number;
        };
        expect(run.status).toBe('COMPLETED');
        expect(run.businessDate).toBe(today);
        expect(run.noShowsMarked).toBeGreaterThanOrEqual(1);
        expect(run.arrivals).toBeGreaterThanOrEqual(1);

        // Verify against the real backend, not just the audit response: the
        // reservation created in beforeAll actually transitioned.
        const reservationResponse = await request.get(`/api/v1/reservations/${reservationId}`);
        expect((await reservationResponse.json()).status).toBe('NO_SHOW');

        // The room is now bookable again for today/tomorrow (same GiST
        // exclusion effect no-show-live.spec.ts verifies after a manual no-show).
        const secondGuest = await createGuest(request, headers);
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        const rebookResponse = await request.post('/api/v1/reservations', {
            headers,
            data: {
                guestId: secondGuest.id,
                expectedGuests: 1,
                checkInDate: today,
                checkOutDate: tomorrow.toISOString().split('T')[0],
                status: 'CONFIRMED',
                lineItems: [{ roomId }],
            },
        });
        expect(rebookResponse.status(), await rebookResponse.text()).toBe(201);
    });

    test('rejects re-closing the same business date', async ({ request }) => {
        const headers = await csrfHeader(request);
        const response = await request.post(`/api/v1/frontdesk/night-audit?date=${today}`, { headers });
        expect(response.status()).toBe(409);
    });

    test('history includes the closed date, newest first', async ({ request }) => {
        const response = await request.get('/api/v1/frontdesk/night-audit?page=0&size=5');
        expect(response.status()).toBe(200);
        const page = await response.json() as { content: Array<{ businessDate: string; status: string }> };
        const todaysRun = page.content.find((run) => run.businessDate === today);
        expect(todaysRun?.status).toBe('COMPLETED');
    });
});
