import { test, expect } from '@playwright/test';
import { ConsoleGuard } from './support/consoleGuard';

// Blocco 1 of the 2026-08-25 exhaustive round. Verified first by hand via
// Chrome MCP (skip link, hamburger+drawer at ~500px, UserMenu, CommandPalette
// input/quick-actions/live-search, RouteAnnouncer document.title, unknown
// route redirect, back, hard refresh — all clean console) — this spec
// freezes that behavior so future runs catch a regression automatically.
test.describe('Blocco 1 — shell & global navigation', () => {
  test('skip link receives focus first and jumps to main content', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    guard.setRoute('/');
    await page.goto('/');
    await page.getByRole('heading', { name: /bentornato|welcome back/i }).waitFor();
    guard.checkpoint('dashboard loaded');

    await page.keyboard.press('Tab');
    const skipLink = page.getByRole('link', { name: /skip to main|vai al contenuto/i });
    await expect(skipLink).toBeFocused();
    guard.checkpoint('skip link focused');
  });

  test('document.title updates via RouteAnnouncer on navigation', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/');
    await page.getByRole('heading', { name: /bentornato|welcome back/i }).waitFor();
    await expect(page).toHaveTitle(/Bacheca|Dashboard/);

    await page.getByRole('link', { name: /^ospiti$|^guests$/i }).click();
    await expect(page).toHaveTitle(/Ospiti|Guests/);
    guard.checkpoint('title updates after nav');
  });

  test('unknown route redirects to dashboard', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/rotta-inesistente-xyz');
    await expect(page).toHaveURL('/');
    await page.getByRole('heading', { name: /bentornato|welcome back/i }).waitFor();
    guard.checkpoint('unknown route redirected');
  });

  test('command palette: opens, live-searches, filters by role-allowed nav', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/');
    await page.getByRole('heading', { name: /bentornato|welcome back/i }).waitFor();

    await page.keyboard.press('Control+k');
    const paletteInput = page.getByPlaceholder(/digita un comando|type a command/i);
    await expect(paletteInput).toBeVisible();
    guard.checkpoint('palette opened');

    await paletteInput.fill('camere');
    await expect(page.getByText(/^camere$|^rooms$/i)).toBeVisible();
    guard.checkpoint('palette filters nav by query');

    await page.keyboard.press('Escape');
    await expect(paletteInput).not.toBeVisible();
    guard.checkpoint('palette closes on Escape');
  });

  test('user menu: opens, shows Settings + Logout, closes on outside click', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/');
    await page.getByRole('heading', { name: /bentornato|welcome back/i }).waitFor();

    await page.locator('#user-menu-trigger').click();
    const menu = page.getByRole('menu');
    await expect(menu).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /impostazioni|settings/i })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /esci|log ?out/i })).toBeVisible();
    guard.checkpoint('user menu open');

    await page.mouse.click(10, 400);
    await expect(menu).not.toBeVisible();
    guard.checkpoint('user menu closes on outside click');
  });

  test('mobile drawer (≈500px): opens with scrim, closes on Escape, focus returns to trigger', async ({ page }) => {
    await page.setViewportSize({ width: 500, height: 800 });
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/');
    await page.getByRole('heading', { name: /bentornato|welcome back/i }).waitFor();

    const hamburger = page.getByRole('button', { name: /nav_menu|menu/i }).first();
    await hamburger.click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText(/hotel pms/i)).toBeVisible();
    guard.checkpoint('drawer opened, logo fully visible (not clipped)');

    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();
    await expect(hamburger).toBeFocused();
    guard.checkpoint('drawer closes on Escape, focus returns to trigger');
  });

  test('logout redirects to /login', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/');
    await page.getByRole('heading', { name: /bentornato|welcome back/i }).waitFor();

    await page.locator('#user-menu-trigger').click();
    await page.getByRole('menuitem', { name: /esci|log ?out/i }).click();
    await expect(page).toHaveURL(/\/login$/);
    guard.checkpoint('logout redirects to login');
  });

  // Regression guard for the 2026-08-25 finding (🔴 CRITICAL, fixed same
  // round): MainLayout.tsx's handleLogout only cleared the client-side
  // Zustand store and navigated to /login — it never called
  // POST /api/v1/auth/logout, so the backend's refresh-token blacklist
  // (AuthController.logout -> RefreshTokenService.blacklist, the one real
  // server-side revocation this stateless-JWT design has) was never
  // triggered. A direct `fetch('/api/v1/auth/me')` right after clicking
  // "Esci" still returned 200 with the same identity.
  //
  // The access token itself has no revocation mechanism anywhere in the
  // stack (by design — 15 min TTL, gateway/AuthController only check
  // expiry) — so /me legitimately keeps working until it naturally expires,
  // fix or no fix. What the fix actually changes: the refresh token gets
  // blacklisted immediately, so once the access token expires, silent
  // refresh must fail and force a real re-login instead of silently
  // extending the "logged out" session for up to 7 more days.
  test('logout blacklists the refresh token server-side (POST /auth/refresh fails after)', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/');
    await page.getByRole('heading', { name: /bentornato|welcome back/i }).waitFor();

    await page.locator('#user-menu-trigger').click();
    await page.getByRole('menuitem', { name: /esci|log ?out/i }).click();
    await expect(page).toHaveURL(/\/login$/);

    // The refresh_token cookie is httpOnly and scoped to /api/v1/auth — the
    // browser still attaches it automatically to this request even though
    // the UI has "logged out" and JS never read its value.
    const refreshResponse = await page.request.post('/api/v1/auth/refresh');
    expect(
      refreshResponse.status(),
      'refresh token must be blacklisted by POST /auth/logout — a refresh after logout should never succeed',
    ).toBe(401);
    guard.checkpoint('post-logout refresh correctly rejected');
  });
});
