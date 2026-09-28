import { test, expect, type APIRequestContext } from '@playwright/test';

// Regression guard for the deterministic-ordering initiative (fix/deterministic-ordering,
// commits d4e3453c..16967c32): sorting a paginated listing on a non-unique column (a
// check-in date, a last name, an issue date) leaves rows with equal keys in an undefined
// order, and Postgres does not keep that order stable across two separate LIMIT/OFFSET
// requests — a row can then be skipped or repeated while a user pages through the list.
// StablePagingPostProcessor (common-web-lib) fixes this globally by appending
// `createdAt desc, id asc` to every Pageable a controller binds, but that safety net is
// opt-in per service (each application class needs `@Import(StablePagingPostProcessor
// .class)`) and can regress silently if a future endpoint bypasses it (a hand-built
// PageRequest in a service, as exportInvoicesCsv/exportGuestsCsv used to). This spec is
// the live, black-box check that closes the loop: for every paginated listing endpoint
// in the platform, walking every page must return exactly as many distinct ids as
// `totalElements` reports, with no duplicates and no gaps — and two independent walks
// must return the identical sequence.
//
// Page size is adaptive per endpoint (see effectivePageSize below), not fixed: a fixed
// size either misses small fixtures entirely (never crosses a page boundary, so never
// exercises the bug this guards against) or, fixed small, multiplies request volume
// across every endpoint enough to trip the gateway's token-bucket rate limiter
// (10 req/s replenish, burst 20 — see api-gateway/src/main/resources/application.yml).
// Sizing down to ~1/3 of the live row count keeps every walk at 3+ pages for whatever
// fixture count exists today. It is capped at MAX_PAGE_SIZE, so past ~60 rows request
// volume grows linearly with the listing's size rather than staying flat — the per-test
// timeout is scaled to the expected page count below for exactly that reason.

const MAX_PAGE_SIZE = 20;
const MAX_PAGES = 400; // guards against an infinite loop if `last` is ever miscomputed
const REQUEST_STAGGER_MS = 150; // > 1/replenishRate (10 req/s), safe under sustained load

interface Row {
    // Most listing DTOs put `id` directly on the row, but InvoiceSearchResultResponse
    // (only `/api/v1/invoices/search`) wraps it as `{ invoice: { id, ... }, guestName }`
    // to carry the resolved guest name alongside the invoice.
    id?: string;
    invoice?: { id: string };
}

interface PageResponse {
    content: Row[];
    totalElements: number;
    last: boolean;
}

function extractId(row: Row): string {
    const id = row.id ?? row.invoice?.id;
    if (!id) {
        throw new Error(`row has neither .id nor .invoice.id: ${JSON.stringify(row)}`);
    }
    return id;
}

function withPage(path: string, size: number, page: number, sort?: string): string {
    const sep = path.includes('?') ? '&' : '?';
    return `${path}${sep}size=${size}&page=${page}${sort ? `&sort=${sort}` : ''}`;
}

async function fetchPage(request: APIRequestContext, url: string): Promise<PageResponse> {
    const response = await request.get(url);
    expect(response.status(), `${url} -> ${response.status()}: ${await response.text()}`).toBe(200);
    return (await response.json()) as PageResponse;
}

/** Small enough to force several pages, large enough to keep request volume sane. */
function effectivePageSize(totalElements: number): number {
    return Math.max(1, Math.min(MAX_PAGE_SIZE, Math.floor(totalElements / 3)));
}

async function walk(request: APIRequestContext, path: string, size: number, sort?: string): Promise<string[]> {
    const ids: string[] = [];
    let expectedTotal: number | undefined;
    for (let page = 0; page < MAX_PAGES; page++) {
        const body = await fetchPage(request, withPage(path, size, page, sort));
        expectedTotal ??= body.totalElements;
        expect(body.totalElements, 'totalElements changed mid-walk — concurrent write, not a paging bug, but breaks this walk\'s assumption').toBe(expectedTotal);
        ids.push(...body.content.map(extractId));
        if (body.last) {
            return ids;
        }
        await new Promise((resolve) => setTimeout(resolve, REQUEST_STAGGER_MS));
    }
    throw new Error(`${path}: never reached the last page within ${MAX_PAGES} pages — "last" flag likely broken`);
}

// One entry per paginated listing endpoint in the platform — every controller taking a
// Spring Data Pageable, across all five services that import StablePagingPostProcessor.
const PAGINATED_ENDPOINTS: Array<{ name: string; path: string; sort?: string }> = [
    { name: 'reservations (default checkInDate sort)', path: '/api/v1/reservations' },
    { name: 'reservations search (client sort=status)', path: '/api/v1/reservations/search', sort: 'status,asc' },
    { name: 'quotations (no @PageableDefault — relies entirely on the global resolver)', path: '/api/v1/quotations' },
    { name: 'stays (default actualCheckInTime sort)', path: '/api/v1/stays' },
    { name: 'reservation groups', path: '/api/v1/reservation-groups' },
    { name: 'rooms (default roomNumber sort)', path: '/api/v1/rooms' },
    { name: 'rooms (client sort=status)', path: '/api/v1/rooms', sort: 'status,asc' },
    { name: 'night audit history', path: '/api/v1/frontdesk/night-audit' },
    { name: 'invoices (default issueDate sort)', path: '/api/v1/invoices' },
    { name: 'invoices search', path: '/api/v1/invoices/search' },
    { name: 'F&B orders', path: '/api/v1/fb/orders' },
    { name: 'guests (default lastName sort)', path: '/api/v1/guests' },
    { name: 'guests (client sort=firstName)', path: '/api/v1/guests', sort: 'firstName,asc' },
    { name: 'guests search', path: '/api/v1/guests/search?keyword=a' },
];

test.describe('Pagination stability across every paginated listing', () => {
    for (const { name, path, sort } of PAGINATED_ENDPOINTS) {
        test(name, async ({ request }) => {
            // Probe totalElements cheaply (size=1) before picking a page size that actually
            // forces several page boundaries for however many rows exist right now.
            const probe = await fetchPage(request, withPage(path, 1, 0, sort));
            const size = effectivePageSize(probe.totalElements);

            // MAX_PAGE_SIZE caps how large a page gets, not how many pages a large listing
            // needs — a listing past ~60 rows walks more pages as it grows, each paying the
            // stagger plus a round trip. The default per-test timeout (playwright-live.config
            // .ts) would silently time out long before MAX_PAGES ever kicks in, so scale it to
            // the actual expected request count for this endpoint's current row count.
            const expectedPagesPerWalk = Math.max(1, Math.ceil(probe.totalElements / size));
            const expectedRequests = 1 /* probe */ + 2 * expectedPagesPerWalk /* two walks */;
            test.setTimeout(Math.max(30_000, expectedRequests * (REQUEST_STAGGER_MS + 300) + 10_000));

            if (probe.totalElements <= 1) {
                // Nothing to page through — the walk below still runs and must still be
                // internally consistent, it just can't exercise a page-boundary bug with
                // 0 or 1 rows. Left visible here rather than silently skipped.
                test.info().annotations.push({
                    type: 'fixture-too-small-to-cross-a-page-boundary',
                    description: `totalElements=${probe.totalElements}`,
                });
            }

            const first = await walk(request, path, size, sort);
            expect(first.length, `rows collected across all pages vs totalElements=${probe.totalElements}`)
                    .toBe(probe.totalElements);
            expect(new Set(first).size, 'duplicate id across two pages — unstable sort').toBe(first.length);

            const second = await walk(request, path, size, sort);
            expect(second, 'row sequence changed between two independent walks').toEqual(first);
        });
    }
});
