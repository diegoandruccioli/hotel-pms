import type { APIRequestContext } from '@playwright/test';
import { SEED_HOTEL_ID } from './hotel';

export const ROOM_TYPE_ID = '8aec8f5c-c19d-4e0f-a63a-27674726bd4b'; // "Doppia Standard", basePrice 90.00 — seeded, see backup/SUMMARY.md

/**
 * Reads the csrf_token cookie from the context's storageState and returns it
 * as the X-CSRF-Token header the double-submit-cookie pattern (T-GW-05)
 * requires on every mutating request. page.route() isn't intercepting here
 * (these are real API calls against the real backend), so nothing injects
 * this header automatically the way the app's own Axios interceptor would.
 */
export async function csrfHeader(request: APIRequestContext): Promise<Record<string, string>> {
    const state = await request.storageState();
    const csrf = state.cookies.find((c) => c.name === 'csrf_token');
    if (!csrf) {
        throw new Error('csrf_token cookie not found — was global.setup.ts\'s login successful?');
    }
    return { 'X-CSRF-Token': csrf.value };
}

export interface CreatedRoom {
    id: string;
    roomNumber: string;
}

export async function createCleanRoom(request: APIRequestContext, headers: Record<string, string>): Promise<CreatedRoom> {
    const roomNumber = `E2E-LIVE-${Date.now()}`;
    const response = await request.post('/api/v1/rooms', {
        headers,
        data: { hotelId: SEED_HOTEL_ID, roomNumber, roomTypeId: ROOM_TYPE_ID, status: 'CLEAN' },
    });
    if (response.status() !== 201) {
        throw new Error(`Failed to create fixture room: ${response.status()} ${await response.text()}`);
    }
    const body = (await response.json()) as { id: string };
    return { id: body.id, roomNumber };
}

export interface GuestOverrides {
    fiscalDetails?: boolean;
}

export interface CreatedGuest {
    id: string;
    email: string;
}

/**
 * Creates a guest fixture. firstName/lastName are deliberately fixed (not
 * unique) — NAME_PATTERN rejects digits, so a timestamp can't go there;
 * uniqueness lives in the email, which is also what specs match on to avoid
 * strict-mode ambiguity when multiple runs leave same-named guests behind.
 *
 * @param fiscalDetails when true, fills the structured address
 * (cap/comune/provincia) FatturaPAServiceImpl requires to export a FATTURA —
 * see GUEST_STRUCTURED_ADDRESS_INCOMPLETE.
 */
export async function createGuest(
    request: APIRequestContext,
    headers: Record<string, string>,
    { fiscalDetails = false }: GuestOverrides = {},
): Promise<CreatedGuest> {
    const email = `live.e2e.${Date.now()}@example.com`;
    const data: Record<string, unknown> = { firstName: 'Live', lastName: 'Suite Guest', email };
    if (fiscalDetails) {
        data.fiscalCode = 'RSSMRA90A01H501U';
        data.cap = '00185';
        data.comune = 'Roma';
        data.provincia = 'RM';
    }
    const response = await request.post('/api/v1/guests', { headers, data });
    if (response.status() !== 201) {
        throw new Error(`Failed to create fixture guest: ${response.status()} ${await response.text()}`);
    }
    return { id: (await response.json()).id, email };
}

/** Alloggiati stato codice for FRANCIA — real value from /stays/lookup/stati, not a mock convenience code. */
export const STATO_FRANCIA = '100000215';

export interface CreatedStay {
    id: string;
    invoiceId: string;
    roomId: string;
}

/**
 * Creates a stay directly via the same POST /api/v1/stays endpoint
 * WalkInCheckInForm.tsx calls — bypassing the walk-in UI form. Legitimate
 * here because the walk-in UI itself is what walk-in-live.spec.ts tests;
 * these other specs need *some* existing stay+invoice as a starting point,
 * not a second exercise of the walk-in form.
 */
export async function createWalkInStay(
    request: APIRequestContext,
    headers: Record<string, string>,
    { roomId, guestId }: { roomId: string; guestId: string },
): Promise<CreatedStay> {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const response = await request.post('/api/v1/stays', {
        headers,
        data: {
            guestId,
            roomId,
            status: 'CHECKED_IN',
            expectedCheckOutDate: tomorrow.toISOString().split('T')[0],
            guests: [{
                firstName: 'Live',
                lastName: 'Suite Guest',
                gender: '1',
                dateOfBirth: '1990-01-01',
                // placeOfBirth holds the stato codice itself for a foreign-born
                // guest — StayGuestFieldSection.tsx sets both _statoDiNascita and
                // placeOfBirth to the same code when the country isn't Italy
                // (no comune to resolve), only using a real comune code when
                // isItalianBorn.
                placeOfBirth: STATO_FRANCIA,
                citizenship: STATO_FRANCIA,
                isPrimaryGuest: true,
                travellerType: 'FAMILIARE',
            }],
        },
    });
    if (response.status() !== 201) {
        throw new Error(`Failed to create fixture stay: ${response.status()} ${await response.text()}`);
    }
    const body = (await response.json()) as CreatedStay;
    if (!body.invoiceId) {
        throw new Error(`Fixture stay created with no invoiceId: ${JSON.stringify(body)}`);
    }
    return body;
}
