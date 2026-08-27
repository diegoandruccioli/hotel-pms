import type { APIRequestContext, Page } from '@playwright/test';
import { ensureQaUser, newLoggedOutContext, uiLoginAs } from '../../qa2408/support/roles';

export { newLoggedOutContext, uiLoginAs, ensureQaUser };

/**
 * Locale-aware counterpart to qa2408's uiLoginAs() (deliberately not
 * modified — it's shared with the qa2408 suite, which always runs against
 * the Playwright-default EN locale). qa2408's `getByLabel(/username/i)`
 * only matches the EN translation ("Username") — under a forced IT locale
 * (`i18nextLng=it` → Login.tsx's field label is "Nome utente", auth.json)
 * it never resolves, and Playwright silently retries `.fill()` until the
 * test's own outer timeout fires. That looked exactly like the app hanging
 * (240s exceeded, `browserContext.close: ... has been closed`) across three
 * separate route-sweep runs before the real cause — an English-only test
 * selector, not a product defect — was found by inspecting the failure's
 * page snapshot: stuck on /login, rendered correctly in Italian.
 */
export async function uiLoginAsLocaleAware(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel(/username|nome utente/i).fill(username);
  await page.getByLabel(/^password$/i).fill(password);
  await page.getByTestId('login-submit').click();
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15_000 });
}

// Dedicated QA accounts for the 2026-08-25 exhaustive round (qa2508). Kept
// separate from qa2408owner/qa2408recept so this round's failures aren't
// confused with the previous round's, and so re-running this suite never
// depends on state qa2408 specs may have mutated (mustChangePassword, role
// reassignment, etc). Same password policy as qa2408 (>=16 chars, 2 upper,
// 2 digits, 2 special).
export const QA25_ADMIN = {
  username: 'qa2508admin',
  password: 'Qa2508Admin!!Pass01',
  email: 'qa2508admin@hotel-pms.local',
  role: 'ADMIN' as const,
};

export const QA25_OWNER = {
  username: 'qa2508owner',
  password: 'Qa2508Owner!!Pass01',
  email: 'qa2508owner@hotel-pms.local',
  role: 'OWNER' as const,
};

export const QA25_RECEPTIONIST = {
  username: 'qa2508recept',
  password: 'Qa2508Recept!!Pass01',
  email: 'qa2508recept@hotel-pms.local',
  role: 'RECEPTIONIST' as const,
};

export const ALL_QA25_ROLES = [QA25_ADMIN, QA25_OWNER, QA25_RECEPTIONIST];

/** Ensures all three qa2508 role accounts exist and are ready to log in (idempotent). */
export async function ensureAllQa25Users(request: APIRequestContext, baseURL: string | undefined): Promise<void> {
  for (const user of ALL_QA25_ROLES) {
    await ensureQaUser(request, baseURL, user);
  }
}
