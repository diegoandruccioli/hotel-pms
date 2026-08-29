import type { APIRequestContext, Browser, BrowserContext, Page } from '@playwright/test';
import { request as playwrightRequest } from '@playwright/test';
import { csrfHeader } from '../../fixtures/api';

/**
 * `browser.newContext()` with no arguments still inherits this project's
 * `use.storageState` (playwright-live.config.ts's 'live' project — the
 * shared ADMIN session) — confirmed empirically: a "fresh" context created
 * this way already carries the admin's jwt/refresh_token/csrf_token cookies
 * before any navigation. An explicit empty storageState is required to get
 * a genuinely logged-out context for a different-identity login.
 */
export async function newLoggedOutContext(browser: Browser): Promise<BrowserContext> {
  return browser.newContext({ storageState: { cookies: [], origins: [] } });
}

// QA-only accounts for the 2026-08-24 full-stack pass, created via the real
// authenticated UserManagementController.createUser path (not a DB insert).
// Passwords satisfy the policy in README.md ("How to Run" — Default Admin
// Credentials): >=16 chars, 2 uppercase, 2 digits, 2 special.
export const QA_OWNER = {
  username: 'qa2408owner',
  password: 'Qa2408Owner!!Pass01',
  email: 'qa2408owner@hotel-pms.local',
  role: 'OWNER' as const,
};

export const QA_RECEPTIONIST = {
  username: 'qa2408recept',
  password: 'Qa2408Recept!!Pass01',
  email: 'qa2408recept@hotel-pms.local',
  role: 'RECEPTIONIST' as const,
};

/**
 * Creates a QA role account via the admin-authenticated `request` context
 * (the 'live' project's storageState) and clears its forced mustChangePassword
 * flag, exactly like global.setup.ts does for LIVE_ADMIN. Idempotent: 409 on
 * a re-run (account already exists) is treated as success.
 */
export async function ensureQaUser(
  request: APIRequestContext,
  baseURL: string | undefined,
  user: { username: string; password: string; email: string; role: string },
): Promise<void> {
  const headers = await csrfHeader(request);
  const createResponse = await request.post('/api/v1/auth/users', { headers, data: user });
  if (createResponse.status() !== 201 && createResponse.status() !== 409) {
    throw new Error(`Failed to create QA user ${user.username}: ${createResponse.status()} ${await createResponse.text()}`);
  }

  // Isolated context — must NOT reuse `request`, which carries the shared
  // admin session's cookies for the whole spec file.
  const userContext = await playwrightRequest.newContext({ baseURL });
  try {
    const loginResponse = await userContext.post('/api/v1/auth/login', {
      data: { username: user.username, password: user.password },
    });
    if (loginResponse.status() !== 200) {
      throw new Error(`Login failed for freshly created QA user ${user.username}: ${loginResponse.status()}`);
    }
    const loginBody = (await loginResponse.json()) as { mustChangePassword?: boolean };
    if (loginBody.mustChangePassword) {
      const csrf = (await userContext.storageState()).cookies.find((c) => c.name === 'csrf_token');
      if (!csrf) throw new Error(`csrf_token missing for ${user.username} right after login`);
      const changeResponse = await userContext.post('/api/v1/auth/change-password', {
        headers: { 'X-CSRF-Token': csrf.value },
        data: { currentPassword: user.password, newPassword: user.password },
      });
      if (changeResponse.status() !== 200) {
        throw new Error(`Failed to clear mustChangePassword for ${user.username}: ${changeResponse.status()} ${await changeResponse.text()}`);
      }
    }
  } finally {
    await userContext.dispose();
  }
}

/** Real UI login as a given role — also exercises the login form itself. */
export async function uiLoginAs(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel(/username/i).fill(username);
  await page.getByLabel(/^password$/i).fill(password);
  await page.getByTestId('login-submit').click();
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15_000 });
}
