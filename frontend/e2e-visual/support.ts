import { expect, type Page } from '@playwright/test';
import { mockAuthMe, mockServerEvents, mockUnhandledApi } from '../e2e/fixtures/mockApi';
import { FROZEN_DATE } from './fixtures/data';

export interface Appearance {
  theme: 'light' | 'dark';
  contrast?: 'normal' | 'high';
  collapsed?: boolean;
}

/** Theme variants every screen is captured in. `hc-dark` is added by hand on a few screens. */
export const THEMES: Array<{ id: string; appearance: Appearance }> = [
  { id: 'light', appearance: { theme: 'light' } },
  { id: 'dark', appearance: { theme: 'dark' } },
  { id: 'hc-light', appearance: { theme: 'light', contrast: 'high' } },
];

export const HC_DARK = { id: 'hc-dark', appearance: { theme: 'dark', contrast: 'high' } as Appearance };

/** Writes the stores' localStorage keys before any app script runs. */
export async function setAppearance(page: Page, { theme, contrast = 'normal', collapsed = false }: Appearance): Promise<void> {
  await page.addInitScript(
    ([t, c, s]) => {
      localStorage.setItem('hotel-pms-theme', t);
      localStorage.setItem('hotel-pms-contrast', c);
      localStorage.setItem('hotel-pms-font-scale', 'normal');
      localStorage.setItem('hotel-pms-sidebar-collapsed', s);
      localStorage.setItem('i18nextLng', 'it');
    },
    [theme, contrast, String(collapsed)],
  );
}

/** Pins "now" so greetings, relative dates and "today" markers never change; timers still run. */
export function freezeClock(page: Page): Promise<void> {
  return page.clock.setFixedTime(new Date(`${FROZEN_DATE}T10:00:00+02:00`));
}

/** Catch-all first (last matching route wins), then the calls every authenticated page makes. */
export async function baseMocks(page: Page, authenticated = true): Promise<void> {
  await mockUnhandledApi(page);
  await mockServerEvents(page);
  if (authenticated) {
    await mockAuthMe(page);
  } else {
    await page.route('**/api/v1/auth/me', (route) => route.fulfill({ status: 401, body: '' }));
    await page.route('**/api/v1/auth/refresh', (route) => route.fulfill({ status: 401, body: '' }));
  }
}

/** Fulfils a JSON body for any request whose URL matches. */
export function json(page: Page, url: string | RegExp | ((url: URL) => boolean), body: unknown): Promise<void> {
  return page.route(url, (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) }),
  );
}

/**
 * Screenshot only once the page is really rendered: URL is the expected one (a missing mock
 * would otherwise bounce to /login and be baked into the baseline), fonts are loaded and no
 * loading indicator is left.
 */
export async function stableShot(page: Page, name: string, path: string): Promise<void> {
  await expect(page).toHaveURL(new RegExp(`${path === '/' ? '/' : path}$`));
  await page.evaluate(() => document.fonts.ready);
  // Every loading indicator (session check, Suspense fallback, M3LoadingState) spins; a spinner
  // that stays means a mock is missing, so the wait times out instead of baking it in.
  await expect(page.locator('.animate-spin, [aria-busy="true"], [role="progressbar"], .animate-pulse')).toHaveCount(0);
  await expect(page).toHaveScreenshot(`${name}.png`, { fullPage: true });
}
