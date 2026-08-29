import type { Page, Route } from '@playwright/test';
import { execFileSync } from 'node:child_process';

// Interruption/fault simulation helpers for Blocco 5 (portal transmission
// under fault) and Blocco 6 (general resilience). All route-based modes only
// intercept the single pattern under test — the rest of the app keeps
// talking to the real backend, so we're testing "this one call fails", not
// "the whole app is offline" (that's `killService` / `context.setOffline`).

export async function abortRequest(page: Page, urlPattern: string | RegExp): Promise<() => Promise<void>> {
  const handler = (route: Route) => route.abort('failed');
  await page.route(urlPattern, handler);
  return () => page.unroute(urlPattern, handler);
}

export async function slowResponse(page: Page, urlPattern: string | RegExp, delayMs: number): Promise<() => Promise<void>> {
  const handler = async (route: Route) => {
    await new Promise((r) => setTimeout(r, delayMs));
    await route.continue();
  };
  await page.route(urlPattern, handler);
  return () => page.unroute(urlPattern, handler);
}

export async function fakeStatus(page: Page, urlPattern: string | RegExp, status: number, body?: unknown): Promise<() => Promise<void>> {
  const handler = (route: Route) =>
    route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(body ?? { type: 'about:blank', title: 'Injected fault', status }),
    });
  await page.route(urlPattern, handler);
  return () => page.unroute(urlPattern, handler);
}

export async function truncatedBody(page: Page, urlPattern: string | RegExp): Promise<() => Promise<void>> {
  const handler = async (route: Route) => {
    const response = await route.fetch();
    const text = await response.text();
    await route.fulfill({
      status: response.status(),
      headers: response.headers(),
      body: text.slice(0, Math.max(1, Math.floor(text.length / 2))),
    });
  };
  await page.route(urlPattern, handler);
  return () => page.unroute(urlPattern, handler);
}

/**
 * Lets the request actually reach the server (so any side effect it causes
 * really happens), then kills the client-side connection before the
 * response arrives — the scenario that catches "retry created a duplicate
 * record" bugs, which a pure `route.abort()` (never sent) cannot catch.
 */
export async function midflightAbort(page: Page, urlPattern: string | RegExp, letThroughMs = 300): Promise<() => Promise<void>> {
  const handler = async (route: Route) => {
    const fetchPromise = route.fetch().catch(() => null);
    await new Promise((r) => setTimeout(r, letThroughMs));
    await fetchPromise; // ensure server processed it before we drop the client side
    await route.abort('failed');
  };
  await page.route(urlPattern, handler);
  return () => page.unroute(urlPattern, handler);
}

const APPLICATION_SERVICES = [
  'frontdesk-service', 'billing-service', 'guest-service', 'fb-service', 'notification-service', 'auth-service',
] as const;
export type ApplicationService = (typeof APPLICATION_SERVICES)[number];

/**
 * Stops/starts a real container to exercise the Resilience4j circuit
 * breakers for real, not simulated. Restricted to the allowlist above —
 * postgres/redis/config-server are never touched (§ "Sicurezza del round").
 * Waits for docker's own healthcheck before returning on restart so the
 * next test step doesn't race a service that's still booting.
 */
function assertAllowlisted(service: ApplicationService, verb: string): void {
  if (!APPLICATION_SERVICES.includes(service)) {
    throw new Error(`Refusing to ${verb} non-allowlisted service: ${service}`);
  }
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

export function stopService(service: ApplicationService): void {
  assertAllowlisted(service, 'stop');
  execFileSync('docker', ['stop', service], { stdio: 'pipe' });
}

export async function startServiceAndWaitHealthy(service: ApplicationService, timeoutMs = 90_000): Promise<void> {
  assertAllowlisted(service, 'start');
  execFileSync('docker', ['start', service], { stdio: 'pipe' });
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const status = execFileSync('docker', ['inspect', '-f', '{{.State.Health.Status}}', service], { stdio: 'pipe' })
      .toString()
      .trim();
    if (status === 'healthy') return;
    if (Date.now() > deadline) throw new Error(`${service} did not become healthy within ${timeoutMs}ms (status=${status})`);
    await sleep(2000);
  }
}
