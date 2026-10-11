import { test as teardown } from '@playwright/test';
import type { APIRequestContext } from '@playwright/test';
import { csrfHeader } from './fixtures/api';
import { LIVE_ADMIN } from './fixtures/hotel';

// Runs once after every "live" spec (even failed ones). createCleanRoom() makes a new
// E2E-LIVE-<timestamp> room per call and many specs leave the stay they open checked-in, so the
// rooms used to pile up in the dev DB (240+) until the room lists were crowded with fixtures.
//
// - Logs in again instead of reusing the setup's storage state: its 15-minute access token has
//   usually expired by the end of a long run, and an API request context does not refresh it.
// - A room that still hosts a stay (OCCUPIED) is never deleted as is: stay, check-in, room-change
//   and extension operations on a removed room answer 404 (only quotations tolerate it). So the
//   teardown closes the fixture stay first, the same real path checkout-live.spec.ts exercises
//   (pay the invoice in full, then check out), and deletes the room only after that. A stay that
//   cannot be closed keeps its room, and is listed at the end.
// - It scans by prefix, so it also cleans what an interrupted earlier run left behind.
// Soft delete only; touches nothing but E2E-LIVE-* rooms of the live-suite hotel.
const FIXTURE_PREFIX = 'E2E-LIVE-';
const PAGE_SIZE = 200;
const CENTS = 100;

interface Page<T> {
    content: T[];
    last?: boolean;
}
interface RoomRow { id: string; roomNumber: string; status: string }
interface StayRow { id: string; roomId: string; invoiceId?: string | null }
interface InvoiceRow { totalAmount: number; status: string; payments: { amount: number }[] }

async function listAll<T>(request: APIRequestContext, path: string): Promise<T[]> {
    const rows: T[] = [];
    for (let page = 0; ; page += 1) {
        const response = await request.get(`${path}${path.includes('?') ? '&' : '?'}size=${PAGE_SIZE}&page=${page}`);
        if (response.status() !== 200) {
            throw new Error(`[teardown] could not list ${path} (${response.status()}), fixture rooms were not cleaned up`);
        }
        const body = (await response.json()) as Page<T>;
        rows.push(...body.content);
        if (body.last !== false) return rows;
    }
}

/** Pays what is still due on the stay's invoice, then checks the stay out. Returns the problem, or null when closed. */
async function closeStay(request: APIRequestContext, headers: Record<string, string>, stay: StayRow): Promise<string | null> {
    if (stay.invoiceId) {
        const invoice = await request.get(`/api/v1/invoices/${stay.invoiceId}`);
        if (invoice.status() !== 200) return `invoice ${stay.invoiceId}: ${invoice.status()}`;
        const { totalAmount, status, payments } = (await invoice.json()) as InvoiceRow;
        const paid = payments.reduce((sum, p) => sum + p.amount, 0);
        const dueCents = Math.round((totalAmount - paid) * CENTS);
        if (status !== 'PAID' && status !== 'CANCELLED' && dueCents > 0) {
            const payment = await request.post(`/api/v1/invoices/${stay.invoiceId}/payments`, {
                headers,
                data: { amount: dueCents / CENTS, paymentMethod: 'CASH' },
            });
            if (payment.status() !== 201) return `payment on ${stay.invoiceId}: ${payment.status()} ${await payment.text()}`;
        }
    }
    const checkout = await request.put(`/api/v1/stays/${stay.id}/check-out`, { headers });
    return checkout.status() === 200 ? null : `check-out ${stay.id}: ${checkout.status()} ${await checkout.text()}`;
}

teardown('remove the E2E-LIVE fixture rooms', async ({ request }) => {
    teardown.setTimeout(300_000);

    const login = await request.post('/api/v1/auth/login', {
        data: { username: LIVE_ADMIN.username, password: LIVE_ADMIN.password },
    });
    if (login.status() !== 200) {
        throw new Error(`[teardown] login failed (${login.status()}), fixture rooms were not cleaned up`);
    }
    const headers = await csrfHeader(request);

    const fixtureRooms = (await listAll<RoomRow>(request, '/api/v1/rooms')).filter((r) => r.roomNumber.startsWith(FIXTURE_PREFIX));
    const occupiedIds = new Set(fixtureRooms.filter((r) => r.status === 'OCCUPIED').map((r) => r.id));
    const deletable = new Set(fixtureRooms.filter((r) => !occupiedIds.has(r.id)).map((r) => r.id));

    // The stays endpoint has no room filter: take every checked-in stay and keep the fixture ones.
    const unclosed: string[] = [];
    if (occupiedIds.size > 0) {
        const stays = (await listAll<StayRow>(request, '/api/v1/stays?status=CHECKED_IN')).filter((s) => occupiedIds.has(s.roomId));
        const failedRooms = new Set<string>();
        for (const stay of stays) {
            const problem = await closeStay(request, headers, stay);
            if (problem) {
                unclosed.push(problem);
                failedRooms.add(stay.roomId);
            }
        }
        // A room is free to delete once none of its stays is left open. One whose stay failed to
        // close was reported above; one with no checked-in stay at all is reported here.
        const withStay = new Set(stays.map((s) => s.roomId));
        for (const id of occupiedIds) {
            if (!withStay.has(id)) unclosed.push(`room ${id}: occupied with no checked-in stay to close`);
            else if (!failedRooms.has(id)) deletable.add(id);
        }
    }

    const statuses = new Map<number, number>();
    for (const id of deletable) {
        const status = (await request.delete(`/api/v1/rooms/${id}`, { headers })).status();
        statuses.set(status, (statuses.get(status) ?? 0) + 1);
    }
    const removed = statuses.get(204) ?? 0;
    console.log(
        `[teardown] removed ${removed} of ${deletable.size} ${FIXTURE_PREFIX}* rooms`
            + ` (${occupiedIds.size} were occupied, ${unclosed.length} problem(s) closing their stays)`,
    );
    for (const problem of unclosed) console.warn(`[teardown] left in place: ${problem}`);
    if (removed < deletable.size) {
        throw new Error(`[teardown] some deletes failed, status counts: ${JSON.stringify([...statuses])}`);
    }
});
