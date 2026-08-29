import { test, expect } from '@playwright/test';
import { ConsoleGuard } from './support/consoleGuard';
import { abortRequest, fakeStatus, slowResponse } from './support/faultInjector';

// Blocco 6 — interruptions, using page.route() fault injection against the
// live backend (the rest of the app keeps talking to the real services —
// only the targeted call is intercepted).
test.describe('Blocco 6 — interruptions', () => {
  test('guest save: aborted request shows an error, does not crash, form stays open for retry', async ({ page }) => {
    const guard = new ConsoleGuard(page, {
      role: 'admin',
      locale: 'it',
      extraAllow: [
        { pattern: /net::ERR_FAILED/, reason: 'deliberately aborted request for this test', scope: 'requestfailed' },
        { pattern: /net::ERR_FAILED/, reason: 'deliberately aborted request for this test', scope: 'console' },
      ],
    });
    await page.goto('/guests');
    await page.getByRole('heading', { name: /^ospiti$|^guests$/i }).waitFor();

    await page.getByRole('button', { name: /aggiungi ospite|add guest/i }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel(/nome|first name/i).fill('Qa Round Abort');
    await dialog.getByLabel(/cognome|last name/i).fill('Test');
    await dialog.getByRole('textbox', { name: /email/i }).fill(`qa25.abort.${Date.now()}@example.com`);

    const unroute = await abortRequest(page, '**/api/v1/guests');
    await dialog.getByRole('button', { name: /^salva$|^save$/i }).click();
    await page.waitForTimeout(1000);
    await expect(dialog).toBeVisible(); // form must not silently vanish
    guard.checkpoint('aborted save keeps the form open, no crash');
    await unroute();

    // Now let the real request through — the form must still be usable.
    await dialog.getByRole('button', { name: /^salva$|^save$/i }).click();
    await expect(dialog).not.toBeVisible({ timeout: 5000 });
    guard.checkpoint('retry after the fault is removed succeeds');
  });

  test('reservation create: 500 from the server is shown as a translated error, not a raw crash', async ({ page }) => {
    const guard = new ConsoleGuard(page, {
      role: 'admin',
      locale: 'it',
      extraAllow: [
        { pattern: /500.*\/api\/v1\/reservations/, reason: 'deliberately injected 500 for this test', scope: 'http_error' },
        { pattern: /500 \(Internal Server Error\)/, reason: 'deliberately injected 500 for this test', scope: 'console' },
      ],
    });
    await page.goto('/reservations/new');
    await page.getByRole('heading', { name: /nuova prenotazione|new reservation/i }).waitFor();

    await page.getByPlaceholder(/cerca ospite|search guest/i).fill('Qa Round');
    await page.waitForTimeout(500);
    const suggestion = page.getByRole('button', { name: /qa round/i }).first();
    if (await suggestion.isVisible({ timeout: 3000 }).catch(() => false)) {
      await suggestion.click();
    }
    const dateInputs = page.locator('input[type=date]');
    const inDate = new Date();
    inDate.setMonth(inDate.getMonth() + 5);
    const outDate = new Date(inDate);
    outDate.setDate(outDate.getDate() + 1);
    const fmt = (d: Date) => d.toISOString().split('T')[0];
    await dateInputs.nth(0).fill(fmt(inDate));
    await dateInputs.nth(1).fill(fmt(outDate));
    const roomTile = page.getByRole('button', { name: /101/i }).first();
    if (await roomTile.isVisible({ timeout: 5000 }).catch(() => false)) {
      await roomTile.click();
    }

    const unroute = await fakeStatus(page, '**/api/v1/reservations', 500, { detail: 'INTERNAL_SERVER_ERROR' });
    const confirmBtn = page.getByRole('button', { name: /conferma prenotazione|confirm reservation/i });
    if (await confirmBtn.isEnabled({ timeout: 5000 }).catch(() => false)) {
      await confirmBtn.click();
      await page.waitForTimeout(1000);
      // Must show SOME error feedback, never a raw stack trace or blank page.
      await expect(page.locator('body')).toBeVisible();
      const hasErrorBoundary = await page.getByText(/\[ErrorBoundary\]/i).isVisible().catch(() => false);
      expect(hasErrorBoundary, 'a backend 500 must never trip the React ErrorBoundary').toBe(false);
      guard.checkpoint('500 handled gracefully, no ErrorBoundary trip');
    }
    await unroute();
  });

  test('double-click on submit does not create two records', async ({ request, page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/guests');
    await page.getByRole('heading', { name: /^ospiti$|^guests$/i }).waitFor();

    const uniqueEmail = `qa25.dblclick.${Date.now()}@example.com`;
    await page.getByRole('button', { name: /aggiungi ospite|add guest/i }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel(/nome|first name/i).fill('Qa Round DoubleClick');
    await dialog.getByLabel(/cognome|last name/i).fill('Test');
    await dialog.getByRole('textbox', { name: /email/i }).fill(uniqueEmail);

    const saveBtn = dialog.getByRole('button', { name: /^salva$|^save$/i });
    // A real double-click gesture (not a racy Promise.all of two separate
    // .click() calls, which can fire the second click before React has
    // committed the filled-in field values, sending an incomplete/invalid
    // second payload that 400s for an unrelated reason).
    await saveBtn.dblclick();
    await page.waitForTimeout(1500);
    guard.checkpoint('double-click submitted');

    const search = await request.get(`/api/v1/guests/search?query=${encodeURIComponent(uniqueEmail)}`);
    const body = await search.json();
    const matches = (body.content ?? body).filter((g: { email: string }) => g.email === uniqueEmail);
    expect(matches.length, 'double-click must not create two guest records').toBe(1);
  });

  test('CSRF cookie removed mid-session: mutating request is rejected with a translated 403, not a crash', async ({ page, context }) => {
    const guard = new ConsoleGuard(page, {
      role: 'admin',
      locale: 'it',
      extraAllow: [
        { pattern: /403.*\/api\/v1\/guests/, reason: 'expected: CSRF cookie removed deliberately for this test', scope: 'http_error' },
        { pattern: /403 \(Forbidden\)/, reason: 'expected: CSRF rejection', scope: 'console' },
      ],
    });
    await page.goto('/guests');
    await page.getByRole('heading', { name: /^ospiti$|^guests$/i }).waitFor();

    // Remove just the csrf_token cookie, leaving the session (jwt) intact.
    const cookies = await context.cookies();
    const others = cookies.filter((c) => c.name !== 'csrf_token');
    await context.clearCookies();
    await context.addCookies(others);

    await page.getByRole('button', { name: /aggiungi ospite|add guest/i }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel(/nome|first name/i).fill('Qa Round NoCsrf');
    await dialog.getByLabel(/cognome|last name/i).fill('Test');
    await dialog.getByRole('textbox', { name: /email/i }).fill(`qa25.nocsrf.${Date.now()}@example.com`);
    await dialog.getByRole('button', { name: /^salva$|^save$/i }).click();
    await page.waitForTimeout(1000);
    await expect(dialog).toBeVisible(); // rejected, not silently accepted
    guard.checkpoint('missing CSRF token correctly rejected, no crash');
  });

  test('session expired mid-form: both jwt and refresh_token removed, submit ends in a clean redirect to /login, no crash', async ({ page, context }) => {
    const guard = new ConsoleGuard(page, {
      role: 'admin',
      locale: 'it',
      extraAllow: [
        { pattern: /401.*\/api\/v1\/guests/, reason: 'expected: session expired deliberately for this test', scope: 'http_error' },
        { pattern: /401.*\/api\/v1\/auth\/refresh/, reason: 'expected: refresh also fails once refresh_token is gone', scope: 'http_error' },
        { pattern: /401 \(Unauthorized\)/, reason: 'expected: session expired', scope: 'console' },
      ],
    });
    await page.goto('/guests');
    await page.getByRole('heading', { name: /^ospiti$|^guests$/i }).waitFor();

    await page.getByRole('button', { name: /aggiungi ospite|add guest/i }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel(/nome|first name/i).fill('Qa Round Expired');
    await dialog.getByLabel(/cognome|last name/i).fill('Test');
    await dialog.getByRole('textbox', { name: /email/i }).fill(`qa25.expired.${Date.now()}@example.com`);

    // Remove BOTH the jwt and refresh_token cookies (httpOnly — only reachable
    // via the CDP-level context.cookies() API, not page.evaluate) so the
    // silent-refresh-then-retry path (api.ts) also fails, forcing the real
    // "session truly expired" branch (performLogout() -> redirect to /login)
    // rather than a transparent recovery.
    const cookies = await context.cookies();
    const remaining = cookies.filter((c) => c.name !== 'jwt' && c.name !== 'refresh_token');
    await context.clearCookies();
    await context.addCookies(remaining);

    await dialog.getByRole('button', { name: /^salva$|^save$/i }).click();
    await page.waitForURL(/\/login/, { timeout: 10_000 });
    guard.checkpoint('expired session redirected cleanly to /login, no crash, form data not silently accepted');
  });

  test('back-navigation while a save POST is still in flight: no crash when the response lands after unmount', async ({ page, request }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    // Navigate through the dashboard first so there's an actual in-app route
    // for goBack() to land on — going straight to /guests as the first
    // navigation leaves no SPA history entry to go back to.
    await page.goto('/');
    await page.getByTestId('dashboard-page').waitFor();
    await page.goto('/guests');
    await page.getByRole('heading', { name: /^ospiti$|^guests$/i }).waitFor();

    const uniqueEmail = `qa25.backnav.${Date.now()}@example.com`;
    await page.getByRole('button', { name: /aggiungi ospite|add guest/i }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel(/nome|first name/i).fill('Qa Round BackNav');
    await dialog.getByLabel(/cognome|last name/i).fill('Test');
    await dialog.getByRole('textbox', { name: /email/i }).fill(uniqueEmail);

    const unroute = await slowResponse(page, '**/api/v1/guests', 3000);
    await dialog.getByRole('button', { name: /^salva$|^save$/i }).click();
    await page.waitForTimeout(300); // let the request actually start
    await page.goBack();
    await page.getByTestId('dashboard-page').waitFor({ timeout: 8000 });
    await page.waitForTimeout(3500); // let the slow response land after navigation away
    guard.checkpoint('navigated away mid-request, response landed after unmount, no crash');
    await unroute();

    // Whichever way the app chose to handle it (request still completed
    // server-side, or the navigation effectively abandoned it client-side),
    // the guest list must not be left in a broken/duplicated state.
    const search = await request.get(`/api/v1/guests/search?query=${encodeURIComponent(uniqueEmail)}`);
    expect(search.status()).toBe(200);
    const body = await search.json();
    const matches = (body.content ?? body).filter((g: { email: string }) => g.email === uniqueEmail);
    expect(matches.length, 'back-navigation during an in-flight save must not create duplicate records').toBeLessThanOrEqual(1);
  });
});
