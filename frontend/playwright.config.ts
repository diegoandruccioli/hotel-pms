import { defineConfig, devices } from '@playwright/test';

// In CI (GitHub Actions sets CI=true automatically):
//   - PLAYWRIGHT_BASE_URL is set to http://localhost:80 (Docker nginx)
//   - No webServer block — Docker Compose provides the frontend
//   - retries: 2 to tolerate transient flakiness
//   - workers: 1 for stability in constrained CI environments
//   - reporters include 'github' for inline PR annotations
//
// Locally:
//   - baseURL defaults to http://localhost:4173 (production bundle via `vite preview`)
//   - webServer builds and serves it, like CI's nginx does: no dev-server transforms, HMR or
//     dependency re-optimisation to race against. It never reuses a running server, so a stale
//     bundle (or a dev server left on another port) cannot leak into a run.
//   - retries: 0, full parallelism

const isCI = !!process.env.CI;
const LOCAL_PORT = 4173;
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? `http://localhost:${LOCAL_PORT}`;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  retries: isCI ? 2 : 0,
  workers: isCI ? 1 : undefined,
  reporter: isCI
    ? [['github'], ['html', { open: 'never' }]]
    : 'html',
  use: {
    baseURL,
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // In CI, use the pre-installed system Chrome (google-chrome-stable on
        // ubuntu-latest) instead of downloading playwright's bundled binary.
        ...(isCI ? { channel: 'chrome' } : {}),
      },
    },
  ],
  // webServer is only used locally — in CI the full Docker stack is already up
  ...(isCI
    ? {}
    : {
        webServer: {
          command: `npx vite build && npx vite preview --port ${LOCAL_PORT} --strictPort`,
          url: `http://localhost:${LOCAL_PORT}`,
          reuseExistingServer: false,
          timeout: 180_000,
        },
      }),
});
