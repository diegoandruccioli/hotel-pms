import { test, expect, type APIRequestContext } from '@playwright/test';
import { SEED_HOTEL_ID } from './fixtures/hotel';

// Acceptance test named directly in the Fase 7 refinement plan: this suite
// must FAIL if commit 27fe70a (StayInvoiceRequest.reservationId made
// nullable + frontdesk-service wired to call billing's addCharge after
// opening the invoice) is reverted. A mocked equivalent of this test
// existed and stayed green through that exact regression — the mock's
// fixed `charges: []` response can't fail no matter what the backend does.
// This spec talks to the real backend end to end, so it can.

const ROOM_TYPE_ID = '8aec8f5c-c19d-4e0f-a63a-27674726bd4b'; // "Doppia Standard", seeded — see backup/SUMMARY.md

async function csrfHeader(request: APIRequestContext, baseURL: string): Promise<Record<string, string>> {
    const cookies = await request.storageState();
    const csrf = cookies.cookies.find((c) => c.name === 'csrf_token');
    if (!csrf) {
        throw new Error(`csrf_token cookie not found — was global.setup.ts's login against ${baseURL} successful?`);
    }
    return { 'X-CSRF-Token': csrf.value };
}

test.describe('Walk-in check-in against the real backend (Fase 7 / item 20)', () => {
    let roomId: string;
    let roomNumber: string;
    let guestEmail: string;

    test.beforeAll(async ({ request, baseURL }) => {
        const headers = await csrfHeader(request, baseURL ?? '');
        roomNumber = `E2E-LIVE-${Date.now()}`;

        const roomResponse = await request.post('/api/v1/rooms', {
            headers,
            data: {
                hotelId: SEED_HOTEL_ID,
                roomNumber,
                roomTypeId: ROOM_TYPE_ID,
                status: 'CLEAN',
            },
        });
        expect(roomResponse.status(), await roomResponse.text()).toBe(201);
        roomId = (await roomResponse.json()).id;

        // Name is deliberately fixed (not unique) — NAME_PATTERN rejects digits,
        // so a timestamp can't go in firstName/lastName. Uniqueness lives in the
        // email instead, which the guest-search option text also renders, so
        // matching on it below never collides with guests from earlier runs.
        guestEmail = `live.e2e.${Date.now()}@example.com`;
        const guestResponse = await request.post('/api/v1/guests', {
            headers,
            data: { firstName: 'Live', lastName: 'Suite Guest', email: guestEmail },
        });
        expect(guestResponse.status(), await guestResponse.text()).toBe(201);
    });

    test('completes a walk-in, opens an invoice, and charges a real ROOM_NIGHT line item', async ({ page }) => {
        await page.goto('/stays/walk-in');

        await expect(page.locator('#walkin-room')).toBeVisible({ timeout: 15000 });
        await page.locator('#walkin-room').selectOption({ value: roomId });

        await page.locator('#walkin-guest').fill('Live Suite Guest');
        const guestOption = page.getByRole('option', { name: guestEmail });
        await expect(guestOption).toBeVisible({ timeout: 5000 });
        await guestOption.click();

        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        await page.locator('#walkin-checkout').fill(tomorrow.toISOString().split('T')[0]);

        // Minimum-path Alloggiati fields (no document/comune required):
        // FAMILIARE traveller type + foreign birthplace. Unlike the mocked
        // walk-in.spec.ts (which fabricates a convenience "ESTERO" entry),
        // the real /stays/lookup/stati table lists actual countries — no
        // generic "foreign" code exists, so pick a real one.
        await page.locator('#traveller-type-0').selectOption({ value: 'FAMILIARE' });
        await page.locator('#stato-nascita-0').fill('FRANC');
        await expect(page.getByRole('option', { name: /FRANCIA/i })).toBeVisible({ timeout: 5000 });
        await page.getByRole('option', { name: /FRANCIA/i }).click();
        // dateOfBirth: NOT part of the "minimum path" the mocked spec exercises
        // (frontend's validateAlloggiatiGuests never checks it — see the bug
        // this live run surfaced, reported separately) but stay_guests.date_of_birth
        // is a real NOT NULL column, so a walk-in without it 500s against a real
        // backend. Filling it here so this spec tests the invoice/charge flow
        // it's actually meant to test, not re-prove the validation gap.
        await page.locator('input[name="dateOfBirth"]').first().fill('1990-01-01');

        const [walkInResponse] = await Promise.all([
            page.waitForResponse((r) => r.url().includes('/api/v1/stays') && r.request().method() === 'POST'),
            page.getByRole('button', { name: /complete walk-in/i }).click(),
        ]);
        const walkInBody = await walkInResponse.text();
        expect(walkInResponse.status(), walkInBody).toBe(201);
        const createdStay = JSON.parse(walkInBody) as { id: string; roomId: string; invoiceId: string | null };
        expect(createdStay.roomId, `stay created for wrong room: ${JSON.stringify(createdStay)}`).toBe(roomId);
        await expect(page).toHaveURL(/\/stays$/, { timeout: 10000 });

        // Verify against the real backend, not the UI: the exact thing
        // 27fe70a fixed — before it, invoice creation itself 400'd on
        // walk-ins (reservationId was NOT NULL at the time), so the invoice
        // never existed and the charge call never ran.
        expect(createdStay.invoiceId, 'stay has no invoiceId — invoice creation failed at check-in').toBeTruthy();
        const invoiceResponse = await page.request.get(`/api/v1/invoices/${createdStay.invoiceId}`);
        expect(invoiceResponse.status()).toBe(200);
        const invoice = (await invoiceResponse.json()) as {
            charges: Array<{ type: string; amount: number }>;
        };
        const roomNightCharge = invoice.charges.find((c) => c.type === 'ROOM_NIGHT');
        expect(roomNightCharge, 'no ROOM_NIGHT charge on the invoice — this is exactly the 27fe70a regression').toBeTruthy();
        expect(roomNightCharge?.amount).toBeGreaterThan(0);
    });
});
