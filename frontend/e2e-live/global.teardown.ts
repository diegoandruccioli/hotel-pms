import { test as teardown } from '@playwright/test';
import { csrfHeader } from './fixtures/api';

// Runs once after every "live" spec (even failed ones). createCleanRoom() makes a new
// E2E-LIVE-<timestamp> room per call and nothing else removes them, so they used to pile up in the
// dev DB (240+ rooms) until the room lists were crowded with fixtures. Soft delete only: the
// quotation/stay history that references them stays readable (QuotationServiceImpl shows a
// placeholder for a removed room).
const FIXTURE_PREFIX = 'E2E-LIVE-';
const PAGE_SIZE = 200;

interface RoomPage {
    content: { id: string; roomNumber: string }[];
    last?: boolean;
}

teardown('remove the E2E-LIVE fixture rooms', async ({ request }) => {
    teardown.setTimeout(120_000);
    const headers = await csrfHeader(request);

    // Collect every id first: deleting while paging would shift the pages under us.
    const fixtureIds: string[] = [];
    for (let page = 0; ; page += 1) {
        const response = await request.get(`/api/v1/rooms?size=${PAGE_SIZE}&page=${page}`);
        if (response.status() !== 200) {
            console.warn(`[teardown] could not list rooms (${response.status()}), skipping the cleanup`);
            return;
        }
        const body = (await response.json()) as RoomPage;
        fixtureIds.push(...body.content.filter((r) => r.roomNumber.startsWith(FIXTURE_PREFIX)).map((r) => r.id));
        if (body.last !== false) break;
    }

    let removed = 0;
    for (const id of fixtureIds) {
        const response = await request.delete(`/api/v1/rooms/${id}`, { headers });
        if (response.status() === 204) removed += 1;
    }
    console.log(`[teardown] removed ${removed} of ${fixtureIds.length} ${FIXTURE_PREFIX}* fixture rooms`);
});
