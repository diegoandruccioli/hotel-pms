import { test, expect } from '@playwright/test';
import { csrfHeader, createCleanRoom, createGuest } from './fixtures/api';

// Verifies the no-show state machine (ReservationServiceImpl.ALLOWED_TRANSITIONS)
// end to end against the real backend: a reservation created for TODAY already
// qualifies for NO_SHOW (the guard is "check-in date not in the future", not
// "strictly in the past" — a same-day no-show at end of day is the real use
// case), so no backdating is needed. ReservationRequest.checkInDate is
// @FutureOrPresent, so a genuinely past date couldn't be created via the API
// anyway.

test.describe('Reservation no-show against the real backend', () => {
    let roomId: string;
    let guestEmail: string;
    let reservationId: string;

    test.beforeAll(async ({ request }) => {
        const headers = await csrfHeader(request);
        const room = await createCleanRoom(request, headers);
        roomId = room.id;
        const guest = await createGuest(request, headers);
        guestEmail = guest.email;

        const today = new Date().toISOString().split('T')[0];
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        const checkOutDate = tomorrow.toISOString().split('T')[0];

        const response = await request.post('/api/v1/reservations', {
            headers,
            data: {
                guestId: guest.id,
                expectedGuests: 1,
                checkInDate: today,
                checkOutDate,
                status: 'CONFIRMED',
                lineItems: [{ roomId }],
            },
        });
        if (response.status() !== 201) {
            throw new Error(`Failed to create fixture reservation: ${response.status()} ${await response.text()}`);
        }
        reservationId = (await response.json()).id as string;
    });

    test('marks a same-day CONFIRMED reservation as no-show and frees the room', async ({ page }) => {
        await page.goto('/reservations');
        await page.getByRole('searchbox').fill(guestEmail);

        const noShowButton = page.getByRole('button', { name: new RegExp(reservationId) });
        await expect(noShowButton).toBeVisible({ timeout: 10000 });
        await noShowButton.click();

        await page.getByRole('button', { name: 'confirm', exact: true }).click();

        // Verify against the real backend, not just the UI toast: the exact
        // thing the ALLOWED_TRANSITIONS + verifyNoShowAllowed guards protect.
        await expect(async () => {
            const getResponse = await page.request.get(`/api/v1/reservations/${reservationId}`);
            expect(getResponse.status()).toBe(200);
            const body = (await getResponse.json()) as { status: string };
            expect(body.status).toBe('NO_SHOW');
        }).toPass({ timeout: 10000 });

        // The GiST exclusion constraint trigger (V14) treats NO_SHOW like
        // CANCELLED for booking_blocking — the same room/dates must now be
        // bookable again by a second reservation with no overlap conflict.
        const headers = await csrfHeader(page.request);
        const today = new Date().toISOString().split('T')[0];
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        const secondGuest = await createGuest(page.request, headers);
        const rebookResponse = await page.request.post('/api/v1/reservations', {
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

    test('rejects marking an already-terminal reservation as no-show again', async ({ page }) => {
        const headers = await csrfHeader(page.request);
        const response = await page.request.get(`/api/v1/reservations/${reservationId}`);
        const { version } = (await response.json()) as { version: number };

        const patchResponse = await page.request.patch(`/api/v1/reservations/${reservationId}/status-and-guests`, {
            headers,
            data: { status: 'CONFIRMED', version },
        });
        expect(patchResponse.status()).toBe(409);
    });
});
