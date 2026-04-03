import { defineConfig, devices } from '@playwright/test';

// In CI (GitHub Actions sets CI=true automatically):
//   - PLAYWRIGHT_BASE_URL is set to http://localhost:80 (Docker nginx)
//   - No webServer block — Docker Compose provides the frontend
//   - retries: 2 to tolerate transient flakiness
//   - workers: 1 for stability in constrained CI environments
//   - reporters include 'github' for inline PR annotations
//
// Locally:
//   - baseURL defaults to http://localhost:5173 (Vite dev server)
//   - webServer starts 'npm run dev' automatically if not already running
//   - retries: 0, full parallelism

const isCI = !!process.env.CI;
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:5173';

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
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  // webServer is only used locally — in CI the full Docker stack is already up
  ...(isCI
    ? {}
    : {
        webServer: {
          command: 'npm run dev',
          url: 'http://localhost:5173',
          reuseExistingServer: true,
        },
      }),
});
