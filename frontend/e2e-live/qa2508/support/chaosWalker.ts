import type { Page } from '@playwright/test';
import type { ConsoleGuard } from './consoleGuard';
import { extractInventory } from './elementSweeper';

/** The exact ARIA-role union Page.getByRole() accepts, without guessing/duplicating Playwright's own type. */
type AriaRole = Parameters<Page['getByRole']>[0];

// Deterministic random walk for Blocco 8. A seed is logged before the first
// step so any failing run can be replayed exactly by passing the same seed
// back in via CHAOS_SEED — "seed loggato ⇒ ogni rotta è riproducibile".

// mulberry32 — small, dependency-free, deterministic PRNG. Good enough for
// test-step selection; not for anything security-sensitive.
function mulberry32(seed: number) {
  let a = seed;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export interface ChaosStep {
  index: number;
  kind: 'click' | 'navigate' | 'back' | 'forward' | 'refresh' | 'resize' | 'palette' | 'escape' | 'random-type';
  detail: string;
}

export interface ChaosOptions {
  seed?: number;
  steps?: number;
  routes: string[];
}

export interface ChaosResult {
  seed: number;
  stepsTaken: ChaosStep[];
  failedAt: ChaosStep | null;
  error: string | null;
}

const KINDS: ChaosStep['kind'][] = ['click', 'navigate', 'back', 'forward', 'refresh', 'resize', 'palette', 'escape', 'random-type'];

/**
 * Runs a seeded random walk starting from a random route. Every step is
 * followed by guard.checkpoint()-equivalent (non-throwing drain, so one bad
 * step doesn't abort the whole 200-step session — the point is to keep
 * walking and log everything that broke, then convert each failure into its
 * own deterministic spec afterward).
 */
export async function runChaosWalk(page: Page, guard: ConsoleGuard, opts: ChaosOptions): Promise<ChaosResult> {
  const seed = opts.seed ?? Date.now() & 0xffffffff;
  const rand = mulberry32(seed);
  const steps = opts.steps ?? 200;
  const stepsTaken: ChaosStep[] = [];
  let failedAt: ChaosStep | null = null;
  let error: string | null = null;

  const pick = <T,>(arr: T[]): T => arr[Math.floor(rand() * arr.length)];

  const startRoute = pick(opts.routes);
  await page.goto(startRoute);
  guard.setRoute(startRoute);

  for (let i = 0; i < steps; i++) {
    const kind = pick(KINDS);
    const step: ChaosStep = { index: i, kind, detail: '' };
    try {
      switch (kind) {
        case 'click': {
          const inventory = await extractInventory(page);
          const safe = inventory.filter((e) => e.role === 'button' || e.role === 'link');
          if (safe.length === 0) break;
          const target = pick(safe);
          step.detail = `${target.role}:${target.name}`;
          await page.getByRole(target.role as AriaRole, { name: target.name, exact: true }).first().click({ timeout: 3000 }).catch(() => {});
          break;
        }
        case 'navigate': {
          const route = pick(opts.routes);
          step.detail = route;
          await page.goto(route);
          guard.setRoute(route);
          break;
        }
        case 'back':
          await page.goBack().catch(() => {});
          break;
        case 'forward':
          await page.goForward().catch(() => {});
          break;
        case 'refresh':
          await page.reload().catch(() => {});
          break;
        case 'resize': {
          const width = 360 + Math.floor(rand() * 1200);
          const height = 600 + Math.floor(rand() * 500);
          step.detail = `${width}x${height}`;
          await page.setViewportSize({ width, height });
          break;
        }
        case 'palette':
          await page.keyboard.press('Control+k').catch(() => {});
          break;
        case 'escape':
          await page.keyboard.press('Escape').catch(() => {});
          break;
        case 'random-type': {
          const chars = 'abc XYZ 123 !@# ​'; // includes a zero-width space deliberately
          const text = Array.from({ length: 5 }, () => chars[Math.floor(rand() * chars.length)]).join('');
          step.detail = text;
          await page.keyboard.type(text, { delay: 5 }).catch(() => {});
          break;
        }
      }
    } catch {
      // step-level failures are expected sometimes (element detached, etc) —
      // only a console/network anomaly surfaced via guard.drain() counts as
      // a "the app broke" finding.
    }
    stepsTaken.push(step);
    const events = guard.drain();
    if (events.length > 0 && !failedAt) {
      failedAt = step;
      error = events.map((e) => `[${e.kind}] ${e.text}`).join(' | ');
    }
  }

  return { seed, stepsTaken, failedAt, error };
}
