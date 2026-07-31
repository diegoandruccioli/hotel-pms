import { test, expect } from '@playwright/test';
import { csrfHeader, createCleanRoom, createGuest } from './fixtures/api';

// Planning board against the real backend (Fase 7 / item 20). PlanningBoard.tsx
// itself is a pure presentational component (rooms/reservations come in as
// props); the real risk lives in CalendarPlanning.tsx's data-fetching contract
// with the real reservations/rooms APIs — field names, date formats,
// guestFullName denormalization — none of which a mocked spec's own
// hand-written fixture can get subtly wrong without ever finding out.

test.describe('Planning board renders a real reservation against the real backend', () => {
    let roomNumber: string;

    test.beforeAll(async ({ request }) => {
        const headers = await csrfHeader(request);
        const room = await createCleanRoom(request, headers);
        roomNumber = room.roomNumber;
        const guest = await createGuest(request, headers);

        // checkIn = today, not tomorrow: CalendarPlanning defaults its view to
        // the CURRENT month with no navigation, so the reservation must start
        // within today's month to render without clicking "next month" first.
        // Using tomorrow broke this near month-end (e.g. run on the 31st,
        // "tomorrow" lands in a different month the test never navigates to).
        const checkIn = new Date();
        const checkOut = new Date();
        checkOut.setDate(checkOut.getDate() + 1);

        const reservationResponse = await request.post('/api/v1/reservations', {
            headers,
            data: {
                guestId: guest.id,
                expectedGuests: 1,
                checkInDate: checkIn.toISOString().split('T')[0],
                checkOutDate: checkOut.toISOString().split('T')[0],
                status: 'CONFIRMED',
                lineItems: [{ roomId: room.id, price: 90.0 }],
            },
        });
        expect(reservationResponse.status(), await reservationResponse.text()).toBe(201);
    });

    test('shows the room row and a reservation bar for the real guest and dates', async ({ page }) => {
        await page.goto('/calendar');

        await expect(page.getByText(roomNumber, { exact: true })).toBeVisible({ timeout: 15000 });

        // ReservationBar renders `${guestFullName} (${status})` as its title
        // and visible text — guestFullName is denormalized by the real
        // backend from the real guest record, not supplied by this test.
        const bar = page.locator(`[title*="CONFIRMED"]`).filter({ hasText: 'Suite Guest' });
        await expect(bar.first()).toBeVisible({ timeout: 10000 });
    });
});
