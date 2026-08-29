import { test, expect, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { ConsoleGuard } from './support/consoleGuard';
import { ROUTES, PARAMETRIC_PLACEHOLDERS, type Role } from './support/allRoutes';
import { QA25_ADMIN, QA25_OWNER, QA25_RECEPTIONIST, newLoggedOutContext, uiLoginAsLocaleAware } from './support/roles';
import { ensureFixtureIds } from './support/bootstrap';
import type { Browser } from '@playwright/test';

// Blocco 2 — full route sweep: every one of the 30 routes in App.tsx,
// under every role that's allowed to see it, in both locales the app
// actually ships (i18n.ts fallbackLng:'en', but this Chrome's real
// navigator.language is it-IT — confirmed via MCP in Blocco 1 — so IT is
// the default an operator actually sees, not a locale override).
//
// Console must stay clean on EVERY load (ConsoleGuard.checkpoint), heading
// must render, axe must report no critical/serious violations. Parametric
// routes get 3 id variants (real / well-formed-but-missing / malformed) —
// checked once under ADMIN here; the same routes get re-walked per-role in
// Blocco 9 (RBAC/IDOR) for permission-specific outcomes.

const MISSING_UUID = '00000000-0000-4000-8000-000000000099';
const MALFORMED_ID = 'not-a-uuid';

// Populated by resolveFixtureIds() in a beforeAll — this round's OWN
// reservation/quotation (see support/bootstrap.ts ensureFixtureIds),
// guaranteed active. Never hardcode a scraped DB id here again: a prior
// hardcoded quotation id 404'd because it belonged to a soft-deleted row
// from an unrelated earlier QA round (Quotation's
// `@SQLRestriction("active = true")`) — a data problem, not a product bug.
let REAL_RESERVATION_ID = '';
let REAL_QUOTATION_ID = '';

function resolvePath(path: string, idVariant: 'real' | 'missing' | 'malformed'): string {
  const reservationId = idVariant === 'real' ? REAL_RESERVATION_ID : idVariant === 'missing' ? MISSING_UUID : MALFORMED_ID;
  const quotationId = idVariant === 'real' ? REAL_QUOTATION_ID : idVariant === 'missing' ? MISSING_UUID : MALFORMED_ID;
  return path
    .replace(PARAMETRIC_PLACEHOLDERS.reservationId, reservationId)
    .replace(PARAMETRIC_PLACEHOLDERS.quotationId, quotationId);
}

async function setLocale(page: Page, locale: 'it' | 'en'): Promise<void> {
  await page.addInitScript((l) => window.localStorage.setItem('i18nextLng', l), locale);
}

async function loadRoute(page: Page, guard: ConsoleGuard, path: string): Promise<void> {
  guard.setRoute(path);
  await page.goto(path);
  // Never wait on networkidle — the SSE /api/v1/events/stream connection
  // never settles (documented trap from the 2026-08-24 pass).
  await page.locator('body').waitFor({ state: 'visible' });
  await page.waitForTimeout(400); // let the lazy chunk + first query settle
}

/**
 * If uiLoginAs() throws (e.g. a transient timeout), the context created just
 * above it must still be closed here — otherwise it leaks for the rest of
 * the run, since the caller's try/finally never receives a `cleanup` to call
 * (pageFor never returns). Across a 9-test file that's up to ~18 contexts;
 * a few leaked ones are enough to degrade later page loads until an
 * unrelated-looking test times out just waiting for /login to render — this
 * is what actually caused the last two tests' "waiting for
 * getByLabel(/username/i)" 30s timeouts, not a product bug.
 */
async function pageFor(browser: Browser, role: Role, locale: 'it' | 'en'): Promise<{ page: Page; cleanup: () => Promise<void> }> {
  const creds = role === 'ADMIN' ? QA25_ADMIN : role === 'OWNER' ? QA25_OWNER : QA25_RECEPTIONIST;
  const context = await newLoggedOutContext(browser);
  try {
    const page = await context.newPage();
    await setLocale(page, locale);
    await uiLoginAsLocaleAware(page, creds.username, creds.password);
    return { page, cleanup: () => context.close() };
  } catch (err) {
    await context.close();
    throw err;
  }
}

test.describe('Blocco 2 — route sweep (30 routes × role × locale)', () => {
  test.beforeAll(async ({ playwright }) => {
    const request = await playwright.request.newContext({ storageState: 'e2e-live/.auth/admin.json' });
    const ids = await ensureFixtureIds(request);
    REAL_RESERVATION_ID = ids.reservationId;
    REAL_QUOTATION_ID = ids.quotationId;
    await request.dispose();
  });

  for (const locale of ['it', 'en'] as const) {
    for (const role of ['ADMIN', 'OWNER', 'RECEPTIONIST'] as const) {
      test(`sweep [${role}, ${locale}]`, async ({ browser }) => {
        // A real 27-route sweep (navigation + 400ms settle + up to 8s
        // heading wait each) can exceed 120s under cold cache / first-run
        // conditions — observed consistently on whichever [role,locale]
        // combination runs first in the loop, regardless of docker/host
        // load; later combinations run fast once caches are warm. 240s
        // gives real headroom without masking a genuine hang.
        test.setTimeout(240_000);
        const { page, cleanup } = await pageFor(browser, role, locale);
        try {
          const guard = new ConsoleGuard(page, { role, locale });
          const allowed = ROUTES.filter((r) => r.roles.includes(role));
          for (const route of allowed) {
            const path = resolvePath(route.path, 'real');
            await loadRoute(page, guard, path);
            await expect(page.getByRole('heading', { name: route.headingPattern }).first()).toBeVisible({ timeout: 8000 });
            guard.checkpoint(`loaded ${path}`);
          }
        } finally {
          await cleanup();
        }
      });
    }
  }

  test('parametric routes: real / missing / malformed id (ADMIN, IT)', async ({ browser }) => {
    test.setTimeout(120_000);
    const { page, cleanup } = await pageFor(browser, 'ADMIN', 'it');
    try {
      const guard = new ConsoleGuard(page, { role: 'ADMIN', locale: 'it' });
      const parametric = ROUTES.filter((r) => r.path.includes('__'));
      for (const route of parametric) {
        for (const variant of ['real', 'missing', 'malformed'] as const) {
          const path = resolvePath(route.path, variant);
          guard.setRoute(path);
          await page.goto(path);
          await page.locator('body').waitFor({ state: 'visible' });
          await page.waitForTimeout(400);
          if (variant === 'real') {
            guard.checkpoint(`real id on ${route.path}`);
            continue;
          }
          // A well-formed-but-nonexistent id correctly 404s server-side;
          // a syntactically invalid one (e.g. "not-a-uuid") fails UUID
          // path-variable binding and correctly 400s instead — both are
          // the expected outcome, not an anomaly. Drain (not checkpoint)
          // so we can filter them out explicitly, while still failing on
          // anything else (500, pageerror, ErrorBoundary trip).
          const events = guard.drain();
          const isExpectedNotFound = (e: { kind: string; text: string }) =>
            (e.kind === 'http_error' && (e.text.includes('404') || e.text.includes('400')))
            || (e.kind === 'console' && /404|400/.test(e.text));
          const unexpected = events.filter((e) => !isExpectedNotFound(e));
          expect(unexpected, `${variant} id on ${route.path}: unexpected event(s) beyond the expected 404\n${JSON.stringify(unexpected, null, 2)}`).toEqual([]);
        }
      }
    } finally {
      await cleanup();
    }
  });

  test('axe accessibility spot-check across key routes (ADMIN, IT)', async ({ browser }) => {
    test.setTimeout(120_000); // 10 full axe scans, each several seconds
    const { page, cleanup } = await pageFor(browser, 'ADMIN', 'it');
    try {
      const spotCheck = ['/', '/guests', '/reservations', '/quotations', '/stays', '/billing', '/restaurant', '/rooms', '/rates', '/settings'];
      for (const path of spotCheck) {
        await page.goto(path);
        await page.locator('body').waitFor({ state: 'visible' });
        await page.waitForTimeout(400);
        const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
        const serious = results.violations.filter((v) => v.impact === 'critical' || v.impact === 'serious');
        expect(serious, `axe critical/serious violations on ${path}: ${JSON.stringify(serious.map((v) => v.id))}`).toEqual([]);
      }
    } finally {
      await cleanup();
    }
  });
});
