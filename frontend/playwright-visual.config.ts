import { defineConfig, devices } from '@playwright/test';

// Visual regression suite: every API call is mocked, the clock is frozen, and the bundle
// is always served by `vite preview` (no Docker), so the only variable left is the
// browser + fonts on the machine. Baselines must therefore be generated on the CI runner
// (see .github/workflows/visual.yml), never on a developer machine: a Windows/macOS PNG
// differs from the Linux one by antialiasing alone.

const isCI = !!process.env.CI;
const PORT = 4173;

export default defineConfig({
  testDir: './e2e-visual',
  // No OS suffix: there is exactly one set of baselines, the Linux one.
  snapshotPathTemplate: '{testDir}/__screenshots__/{projectName}/{arg}{ext}',
  fullyParallel: false,
  workers: 1,
  retries: isCI ? 1 : 0,
  reporter: isCI ? [['github'], ['html', { open: 'never' }]] : 'html',
  expect: {
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.01,
      animations: 'disabled',
      caret: 'hide',
      scale: 'css',
    },
  },
  use: {
    baseURL: `http://localhost:${PORT}`,
    locale: 'it-IT',
    timezoneId: 'Europe/Rome',
    contextOptions: { reducedMotion: 'reduce' },
    colorScheme: 'light',
    ...(isCI ? { channel: 'chrome' } : {}),
  },
  projects: [
    {
      name: 'desktop',
      grepInvert: /@mobile/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } },
    },
    {
      name: 'mobile',
      grep: /@mobile/,
      use: { ...devices['Desktop Chrome'], viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true },
    },
  ],
  webServer: {
    command: `npx vite build && npx vite preview --port ${PORT} --strictPort`,
    url: `http://localhost:${PORT}`,
    reuseExistingServer: false,
    timeout: 180_000,
  },
});
