import { test, expect } from '@playwright/test';
import { attachQaListeners, setContext, logCustom } from './support/qaListeners';
import { ensureQaUser, uiLoginAs, newLoggedOutContext, QA_OWNER, QA_RECEPTIONIST } from './support/roles';

// 2026-08-24 QA pass — Phase 3 (RBAC). frontend/src/App.tsx:112-117 gates
// /owner-dashboard, /admin/users, /profile/hotel, /settings/system,
// /settings/city-tax behind OWNER_ADMIN_ROLES. Verifies the RECEPTIONIST is
// blocked BOTH in the UI (guard redirect) AND at the API (403 direct call),
// re-checking the exact pattern docs/EXPLORATORY_TEST_2026-08.md round 2 #5
// found unguarded on InvoiceController — don't assume that fix holds,
// verify it live.

const OWNER_ADMIN_ONLY_ROUTES = ['/owner-dashboard', '/admin/users', '/profile/hotel', '/settings/system', '/settings/city-tax'];

test.describe.configure({ mode: 'serial' });

test.beforeAll(async ({ request, baseURL }) => {
  await ensureQaUser(request, baseURL, QA_OWNER);
  await ensureQaUser(request, baseURL, QA_RECEPTIONIST);
});

test.describe('QA 2026-08-24 — RECEPTIONIST is blocked from ADMIN/OWNER routes', () => {
  for (const routePath of OWNER_ADMIN_ONLY_ROUTES) {
    test(`UI guard redirects RECEPTIONIST away from ${routePath}`, async ({ browser, baseURL }) => {
      const context = await newLoggedOutContext(browser);
      attachQaListeners(context, { role: 'RECEPTIONIST', locale: 'it' });
      const page = await context.newPage();
      setContext(routePath, 'rbac-ui-guard');
      await uiLoginAs(page, QA_RECEPTIONIST.username, QA_RECEPTIONIST.password);
      await page.goto(routePath);
      await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => undefined);
      const url = new URL(page.url());
      if (OWNER_ADMIN_ONLY_ROUTES.includes(url.pathname)) {
        logCustom('rbac_ui_guard_bypassed', { routePath, finalUrl: page.url(), role: 'RECEPTIONIST' });
      }
      expect(OWNER_ADMIN_ONLY_ROUTES.includes(url.pathname), `RECEPTIONIST reached ${routePath} in the UI`).toBe(false);
      await context.close();
    });
  }
});

test.describe('QA 2026-08-24 — RECEPTIONIST is blocked from ADMIN/OWNER APIs directly', () => {
  test('RECEPTIONIST gets 403 from /api/v1/reports/owner (already known-good, re-verified)', async ({ browser, baseURL }) => {
    const context = await newLoggedOutContext(browser);
    const page = await context.newPage();
    await uiLoginAs(page, QA_RECEPTIONIST.username, QA_RECEPTIONIST.password);
    const response = await page.request.get('/api/v1/reports/owner?startDate=2000-01-01&endDate=2099-12-31');
    expect(response.status()).toBe(403);
    await context.close();
  });

  test('RECEPTIONIST gets 403 from /api/v1/auth/users (user management)', async ({ browser }) => {
    const context = await newLoggedOutContext(browser);
    const page = await context.newPage();
    await uiLoginAs(page, QA_RECEPTIONIST.username, QA_RECEPTIONIST.password);
    const response = await page.request.get('/api/v1/auth/users');
    expect(response.status()).toBe(403);
    await context.close();
  });

  test('RECEPTIONIST is rejected from invoice fiscal operations (re-verifying R2 #5 fix)', async ({ browser }) => {
    const context = await newLoggedOutContext(browser);
    const page = await context.newPage();
    await uiLoginAs(page, QA_RECEPTIONIST.username, QA_RECEPTIONIST.password);
    const exportResponse = await page.request.get('/api/v1/invoices/export?from=2026-01-01&to=2026-12-31');
    if (exportResponse.status() !== 403) {
      logCustom('rbac_regression', {
        finding: 'R2#5 InvoiceController RBAC regressed',
        endpoint: '/api/v1/invoices/export',
        status: exportResponse.status(),
        role: 'RECEPTIONIST',
      });
    }
    expect(exportResponse.status(), 'RECEPTIONIST should not access the fiscal batch export').toBe(403);
    await context.close();
  });

  test('RECEPTIONIST cannot change hotel GDPR retention policy (re-verifying R2 #6 fix)', async ({ browser }) => {
    const context = await newLoggedOutContext(browser);
    const page = await context.newPage();
    await uiLoginAs(page, QA_RECEPTIONIST.username, QA_RECEPTIONIST.password);
    const csrf = (await context.cookies()).find((c) => c.name === 'csrf_token');
    const response = await page.request.put('/api/v1/guests/settings', {
      headers: csrf ? { 'X-CSRF-Token': csrf.value } : {},
      data: { guestRetentionYears: 9 },
    });
    if (response.status() !== 403) {
      logCustom('rbac_regression', {
        finding: 'R2#6 GuestPrivacySettingsController RBAC regressed',
        endpoint: '/api/v1/guests/settings',
        status: response.status(),
        role: 'RECEPTIONIST',
      });
    }
    expect(response.status()).toBe(403);
    await context.close();
  });
});

test.describe('QA 2026-08-24 — OWNER has access equivalent to ADMIN on gated routes', () => {
  for (const routePath of OWNER_ADMIN_ONLY_ROUTES) {
    test(`OWNER can reach ${routePath} in the UI`, async ({ browser }) => {
      const context = await newLoggedOutContext(browser);
      attachQaListeners(context, { role: 'OWNER', locale: 'it' });
      const page = await context.newPage();
      setContext(routePath, 'rbac-owner-access');
      await uiLoginAs(page, QA_OWNER.username, QA_OWNER.password);
      await page.goto(routePath);
      await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => undefined);
      await expect(page).toHaveURL(routePath);
      await context.close();
    });
  }
});
