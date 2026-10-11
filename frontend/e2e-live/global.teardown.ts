import { test as teardown } from '@playwright/test';
import { csrfHeader } from './fixtures/api';
import { LIVE_ADMIN } from './fixtures/hotel';

// Runs once after every "live" spec (even failed ones). createCleanRoom() makes a new
// E2E-LIVE-<timestamp> room per call and nothing else removes them, so they used to pile up in the
// dev DB (240+ rooms) until the room lists were crowded with fixtures. Soft delete only.
//
// - Logs in again instead of reusing the setup's storage state: its 15-minute access token has
//   usually expired by the end of a long run, and an API request context does not refresh it.
// - Leaves OCCUPIED rooms alone: deleting a room that still hosts a stay would make stay and
//   reservation operations on it answer 404 (only quotations tolerate a removed room). They are
//   picked up by the next run once checked out.
const FIXTURE_PREFIX = 'E2E-LIVE-';
const PAGE_SIZE = 200;

interface RoomPage {
    content: { id: string; roomNumber: string; status: string }[];
    last?: boolean;
}

teardown('remove the E2E-LIVE fixture rooms', async ({ request }) => {
    teardown.setTimeout(120_000);

    const login = await request.post('/api/v1/auth/login', {
        data: { username: LIVE_ADMIN.username, password: LIVE_ADMIN.password },
    });
    if (login.status() !== 200) {
        throw new Error(`[teardown] login failed (${login.status()}), fixture rooms were not cleaned up`);
    }
    const headers = await csrfHeader(request);

    // Collect every id first: deleting while paging would shift the pages under us.
    const fixtureIds: string[] = [];
    let skippedOccupied = 0;
    for (let page = 0; ; page += 1) {
        const response = await request.get(`/api/v1/rooms?size=${PAGE_SIZE}&page=${page}`);
        if (response.status() !== 200) {
            throw new Error(`[teardown] could not list rooms (${response.status()}), fixture rooms were not cleaned up`);
        }
        const body = (await response.json()) as RoomPage;
        for (const room of body.content.filter((r) => r.roomNumber.startsWith(FIXTURE_PREFIX))) {
            if (room.status === 'OCCUPIED') skippedOccupied += 1;
            else fixtureIds.push(room.id);
        }
        if (body.last !== false) break;
    }

    const statuses = new Map<number, number>();
    for (const id of fixtureIds) {
        const status = (await request.delete(`/api/v1/rooms/${id}`, { headers })).status();
        statuses.set(status, (statuses.get(status) ?? 0) + 1);
    }
    const removed = statuses.get(204) ?? 0;
    console.log(
        `[teardown] removed ${removed} of ${fixtureIds.length} ${FIXTURE_PREFIX}* rooms`
            + ` (${skippedOccupied} occupied left in place)`,
    );
    if (removed < fixtureIds.length) {
        throw new Error(`[teardown] some deletes failed, status counts: ${JSON.stringify([...statuses])}`);
    }
});
