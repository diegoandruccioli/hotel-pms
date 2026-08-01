import { defineConfig, devices } from '@playwright/test';

// Fase 7 (AUDIT_ANALISI_2026-07.md item 20) — E2E against the REAL Docker
// stack, no page.route() mocking. Complements (does not replace) `e2e/`:
// the mocked suite stays the fast per-push gate; this suite exists for the
// handful of flows where the bug lives in the backend contract, not the
// rendering — a mock can't catch those by construction (see commit
// 27fe70a: the mock returned `charges: []` and the equivalent mocked test
// stayed green while checkout was actually broken against a real backend).
//
// NOT wired into CI yet (deliberate — see Fase 7 scoping decision,
// 2026-07-30): run manually against a live stack.
//
// Usage:
//   docker compose up -d   (full stack, or at minimum
//     postgres/redis/config-server/auth/frontdesk/billing/guest/gateway/frontend —
//     none of these are profile-gated)
//   cd frontend && npm run test:e2e:live

const baseURL = process.env.PLAYWRIGHT_LIVE_BASE_URL ?? 'http://localhost';
const STORAGE_STATE = 'e2e-live/.auth/admin.json';

export default defineConfig({
  testDir: './e2e-live',
  fullyParallel: false, // fixtures created via API share hotel-scoped state; avoid cross-test races
  retries: 0,
  workers: 1,
  timeout: 30_000,
  reporter: 'html',
  use: {
    baseURL,
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'setup',
      testMatch: /global\.setup\.ts/,
    },
    {
      name: 'live',
      use: {
        ...devices['Desktop Chrome'],
        storageState: STORAGE_STATE,
      },
      dependencies: ['setup'],
    },
  ],
});
