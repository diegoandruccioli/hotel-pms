import type { Page } from '@playwright/test';

/**
 * Shared Playwright route mocks for endpoints hit by nearly every spec.
 *
 * Centralized here after a CI regression (2026-08) where the Fase 0-2 UI/UX
 * work (React Query migration + the day-sheet endpoint replacing five
 * separate client calls) changed what Dashboard/Housekeeping actually call,
 * but the e2e specs kept mocking the old endpoints inline — each spec had
 * copy-pasted its own `page.route()` setup, so the drift went unnoticed
 * until every one of them broke at once. Mocking day-sheet/owner-summary in
 * one place means the next endpoint change only needs updating here.
 */

export interface MockUser {
  username: string;
  role: 'ADMIN' | 'OWNER' | 'RECEPTIONIST';
  sub: string;
  mustChangePassword: boolean;
}

export const DEFAULT_MOCK_USER: MockUser = {
  username: 'admin',
  role: 'ADMIN',
  sub: 'admin',
  mustChangePassword: false,
};

export function mockAuthMe(page: Page, user: MockUser = DEFAULT_MOCK_USER): Promise<void> {
  return page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(user) }),
  );
}

/** Local date, matching dashboardService.getTodayDateString() — not
 * toISOString(), which is UTC and can disagree with the local date near
 * local midnight. */
function todayLocalDateString(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

/** Mirrors `DaySheetResponse` — see frontend/src/types/daySheet.types.ts.
 * Flat scalars, not a Spring Page. `roomStatusCounts` entries default to 0
 * for every status so callers don't need to special-case a missing key. */
export interface MockDaySheet {
  date: string;
  todayArrivals: number;
  todayDepartures: number;
  guestsInHouse: number;
  currentStays: number;
  availableRooms: number;
  roomStatusCounts: Partial<Record<'CLEAN' | 'DIRTY' | 'MAINTENANCE' | 'OCCUPIED', number>>;
}

export const DEFAULT_MOCK_DAY_SHEET: MockDaySheet = {
  date: todayLocalDateString(),
  todayArrivals: 1,
  todayDepartures: 0,
  guestsInHouse: 2,
  currentStays: 1,
  availableRooms: 1,
  roomStatusCounts: { CLEAN: 1, DIRTY: 1, MAINTENANCE: 0, OCCUPIED: 1 },
};

export function mockDaySheet(page: Page, overrides: Partial<MockDaySheet> = {}): Promise<void> {
  const body = { ...DEFAULT_MOCK_DAY_SHEET, ...overrides };
  return page.route('**/api/v1/frontdesk/day-sheet**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) }),
  );
}

/** Mirrors `OwnerFinancialSummaryDto` — see
 * frontend/src/types/ownerReport.types.ts. Registered by callers *after*
 * any broader `**\/api/v1/reports/owner**` mock, since Playwright gives
 * priority to the most-recently-registered matching route and the summary
 * path is a suffix of the full-report path. */
export interface MockOwnerSummary {
  startDate: string;
  endDate: string;
  totalRevenue: number;
  totalInvoices: number;
  paidInvoices: number;
  pendingRevenue: number;
}

export const DEFAULT_MOCK_OWNER_SUMMARY: MockOwnerSummary = {
  startDate: '2000-01-01',
  endDate: '2099-12-31',
  totalRevenue: 1000,
  totalInvoices: 5,
  paidInvoices: 4,
  pendingRevenue: 150,
};

export function mockOwnerSummary(page: Page, overrides: Partial<MockOwnerSummary> = {}): Promise<void> {
  const body = { ...DEFAULT_MOCK_OWNER_SUMMARY, ...overrides };
  return page.route('**/api/v1/reports/owner/summary**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) }),
  );
}
