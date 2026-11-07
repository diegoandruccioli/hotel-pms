import { test, type Page } from '@playwright/test';
import { mockDaySheet, mockDueOutStays, mockOwnerSummary, mockTodayArrivals } from '../e2e/fixtures/mockApi';
import {
  DAY_SHEET, DUE_OUT_STAYS, FROZEN_DATE, GUESTS, INVOICES, OWNER_SUMMARY, RESERVATIONS, ROOMS, TREND_POINTS, springPage,
} from './fixtures/data';
import {
  HC_DARK, THEMES, baseMocks, freezeClock, json, setAppearance, stableShot, type Appearance,
} from './support';

interface Screen {
  name: string;
  path: string;
  /** Per-screen API mocks, registered after the shared ones. */
  mock?: (page: Page) => Promise<void>;
  authenticated?: boolean;
  /** Extra captures beyond the three standard themes. */
  extra?: Array<{ id: string; appearance: Appearance }>;
  mobile?: boolean;
}

const mockDashboard = async (page: Page): Promise<void> => {
  await mockDaySheet(page, DAY_SHEET);
  // After mockDaySheet, which registers an empty trend stamped with the real date.
  await json(page, (url) => url.pathname.endsWith('/api/v1/frontdesk/day-sheet/trend'), {
    from: TREND_POINTS[0].date, to: FROZEN_DATE, points: TREND_POINTS,
  });
  await mockOwnerSummary(page, OWNER_SUMMARY);
  await mockTodayArrivals(page, [RESERVATIONS[0]]);
  await mockDueOutStays(page, DUE_OUT_STAYS);
};

const mockHousekeeping = async (page: Page): Promise<void> => {
  await mockDaySheet(page, DAY_SHEET);
  await json(page, /\/api\/v1\/rooms/, springPage(ROOMS, 200));
};

const SCREENS: Screen[] = [
  {
    name: 'login',
    path: '/login',
    authenticated: false,
    mobile: true,
  },
  {
    name: 'dashboard',
    path: '/',
    mock: mockDashboard,
    mobile: true,
    extra: [
      { id: 'hc-dark', appearance: HC_DARK.appearance },
      { id: 'compact', appearance: { theme: 'light', collapsed: true } },
    ],
  },
  {
    name: 'reservations',
    path: '/reservations',
    mock: async (page) => {
      await json(page, /\/api\/v1\/reservations/, springPage(RESERVATIONS));
      await json(page, /\/api\/v1\/rooms/, springPage(ROOMS, 200));
    },
  },
  {
    name: 'guests',
    path: '/guests',
    mock: (page) => json(page, /\/api\/v1\/guests/, springPage(GUESTS)),
  },
  {
    name: 'calendar',
    path: '/calendar',
    mock: async (page) => {
      await json(page, /\/api\/v1\/reservations/, springPage(RESERVATIONS, 500));
      await json(page, /\/api\/v1\/rooms/, springPage(ROOMS, 200));
    },
  },
  {
    name: 'billing',
    path: '/billing',
    mock: async (page) => {
      await mockOwnerSummary(page, OWNER_SUMMARY);
      await json(page, /\/api\/v1\/invoices/, springPage(INVOICES));
    },
  },
  {
    name: 'housekeeping',
    path: '/housekeeping',
    mock: mockHousekeeping,
    mobile: true,
    extra: [{ id: 'hc-dark', appearance: HC_DARK.appearance }],
  },
  {
    name: 'appearance',
    path: '/settings/appearance',
  },
];

async function capture(page: Page, screen: Screen, id: string, appearance: Appearance): Promise<void> {
  await setAppearance(page, appearance);
  await freezeClock(page);
  await baseMocks(page, screen.authenticated ?? true);
  await screen.mock?.(page);
  await page.goto(screen.path);
  await stableShot(page, `${screen.name}-${id}`, screen.path);
}

for (const screen of SCREENS) {
  test.describe(screen.name, () => {
    for (const { id, appearance } of THEMES) {
      test(id, async ({ page }) => capture(page, screen, id, appearance));
    }
    for (const { id, appearance } of screen.extra ?? []) {
      test(id, async ({ page }) => capture(page, screen, id, appearance));
    }
    if (screen.mobile) {
      test('light @mobile', async ({ page }) => capture(page, screen, 'light', { theme: 'light' }));
    }
  });
}
