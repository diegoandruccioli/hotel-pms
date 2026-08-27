import { writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { test, expect } from '@playwright/test';
import { ConsoleGuard } from './support/consoleGuard';
import { runChaosWalk } from './support/chaosWalker';
import { uiLoginAsLocaleAware } from './support/roles';
import { QA25_ADMIN, QA25_OWNER, QA25_RECEPTIONIST } from './support/roles';
import { ROUTES, type Role } from './support/allRoutes';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ARTIFACT_DIR = path.resolve(__dirname, '..', '..', '..', 'qa-artifacts', '2026-08-25');
mkdirSync(ARTIFACT_DIR, { recursive: true });

// Blocco 8 — deterministic random-walk chaos, one session per role. Every
// step is followed by a non-throwing ConsoleGuard drain (chaosWalker.ts) so
// one broken step doesn't abort the whole session — the point is to keep
// walking through all `steps` and record everything that broke, converting
// any real finding into its own deterministic spec afterward rather than
// asserting inline (a chaos run's job is discovery, not pass/fail on a
// single assertion).
const ROLES = [
  { ...QA25_ADMIN, locale: 'it' as const },
  { ...QA25_OWNER, locale: 'it' as const },
  { ...QA25_RECEPTIONIST, locale: 'en' as const },
];

// 60 steps/role rather than the plan's 200 — real backend, real network
// round-trips per step; 200×3 would run well past this suite's practical
// time budget. 60 still exercises hundreds of state transitions across
// click/navigate/back/forward/refresh/resize/palette/escape/random-type.
const STEPS_PER_SESSION = Number(process.env.CHAOS_STEPS ?? 60);

test.describe('Blocco 8 — chaos (deterministic random walk)', () => {
  for (const role of ROLES) {
    test(`random walk: ${role.role} (seed logged for replay)`, async ({ page }) => {
      test.setTimeout(180_000);
      await uiLoginAsLocaleAware(page, role.username, role.password);

      const guard = new ConsoleGuard(page, {
        role: role.role,
        locale: role.locale,
        extraAllow: [
          { pattern: /net::ERR_ABORTED/, reason: 'chaos walk includes rapid navigate/back/refresh — a genuinely interrupted navigation, not a real failure', scope: 'requestfailed' },
        ],
      });

      const roleName = role.role as Role;
      const routes = ROUTES
        .filter((r) => r.roles.includes(roleName))
        .filter((r) => !r.path.includes('__')) // skip parametric placeholders — Blocco 2 already covers real/missing/malformed ids explicitly
        .map((r) => r.path);

      const result = await runChaosWalk(page, guard, { routes, steps: STEPS_PER_SESSION });

      const logPath = path.join(ARTIFACT_DIR, `chaos-${role.role.toLowerCase()}-seed-${result.seed}.json`);
      writeFileSync(logPath, JSON.stringify(result, null, 2), 'utf-8');

      expect(
        result.failedAt === null,
        result.failedAt
          ? `chaos walk broke something at step ${result.failedAt.index} (${result.failedAt.kind}: ${result.failedAt.detail})\n`
            + `error: ${result.error}\nseed=${result.seed} (replay: CHAOS_SEED=${result.seed}) — full trace: ${logPath}`
          : '',
      ).toBe(true);
    });
  }
});
