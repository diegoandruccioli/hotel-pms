import { test, expect } from '@playwright/test';

// ---------------------------------------------------------------------------
// Mock data
// ---------------------------------------------------------------------------
const MOCK_USER = {
  username: 'admin',
  roles: ['ROLE_ADMIN'],
};

const LOGIN_CREDENTIALS = {
  username: 'admin',
  password: 'password',
};

// ---------------------------------------------------------------------------
// Test: Full Happy Path – login → redirect to dashboard → assert content
// ---------------------------------------------------------------------------
test.describe('Authentication – Happy Path', () => {
  test('should login successfully and display the dashboard', async ({ page }) => {
    // -----------------------------------------------------------------------
    // PHASE 1 – Setup: intercept API calls with a stateful counter so that:
    //   • /me call #1 (app bootstrap) → 401  (user not yet logged in)
    //   • /me call #2 (post-login)    → 200  (user now authenticated)
    //   • /auth/login                 → 200  (credentials accepted)
    // -----------------------------------------------------------------------
    let meCallCount = 0;

    await page.route('**/api/v1/auth/me', async (route) => {
      meCallCount += 1;
      if (meCallCount === 1) {
        // Bootstrap check – not authenticated yet
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ error: 'Unauthorized' }),
        });
      } else {
        // Post-login check – user is now authenticated
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(MOCK_USER),
        });
      }
    });

    await page.route('**/api/v1/auth/login', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: {
          'Set-Cookie': 'SESSION=mock-session-token; Path=/; HttpOnly; SameSite=Lax',
        },
        body: JSON.stringify({ message: 'Login successful' }),
      });
    });

    // -----------------------------------------------------------------------
    // PHASE 2 – Navigate to the root URL.
    //   The app will call /me (→ 401), then redirect to /login.
    // -----------------------------------------------------------------------
    await page.goto('/');

    // Confirm we land on the login page
    await expect(page).toHaveURL(/.*\/login/);
    await expect(page.locator('[data-testid="login-form"]')).toBeVisible();

    // -----------------------------------------------------------------------
    // PHASE 3 – Fill the login form using stable, i18n-agnostic selectors.
    //   The inputs already have id="username" and id="password" in the DOM.
    // -----------------------------------------------------------------------
    await page.getByRole('textbox', { name: /username/i }).fill(LOGIN_CREDENTIALS.username);
    await page.getByLabel(/password/i).fill(LOGIN_CREDENTIALS.password);

    // -----------------------------------------------------------------------
    // PHASE 4 – Submit the form and wait for the POST /login response.
    // -----------------------------------------------------------------------
    const loginResponsePromise = page.waitForResponse('**/api/v1/auth/login');
    await page.locator('[data-testid="login-submit"]').click();
    const loginResponse = await loginResponsePromise;
    expect(loginResponse.status()).toBe(200);

    // -----------------------------------------------------------------------
    // PHASE 5 – Assert successful redirect to the dashboard (root URL).
    // -----------------------------------------------------------------------
    await expect(page).toHaveURL(/\/$/, { timeout: 10_000 });

    // -----------------------------------------------------------------------
    // PHASE 6 – Assert key dashboard elements are visible.
    //   Use data-testid selectors so assertions are locale-independent.
    // -----------------------------------------------------------------------
    const dashboardPage = page.locator('[data-testid="dashboard-page"]');
    await expect(dashboardPage).toBeVisible({ timeout: 10_000 });

    // The heading contains the username interpolated by i18n
    const heading = page.locator('[data-testid="dashboard-heading"]');
    await expect(heading).toBeVisible();
    await expect(heading).toContainText('admin'); // username is locale-independent

    // The stats grid must render at least one stat card
    const statsGrid = page.locator('[data-testid="stats-grid"]');
    await expect(statsGrid).toBeVisible();
    await expect(statsGrid.locator('.bg-white').first()).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // Bonus test: assert the login page is accessible (not authenticated check)
  // -------------------------------------------------------------------------
  test('should show the login form when not authenticated', async ({ page }) => {
    // Mock /me to always return 401
    await page.route('**/api/v1/auth/me', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Unauthorized' }),
      });
    });

    await page.goto('/');

    // Should be redirected to /login
    await expect(page).toHaveURL(/\/login/);

    // Stable ARIA selectors – locale independent
    await expect(page.getByRole('textbox', { name: /username/i })).toBeVisible();
    await expect(page.getByLabel(/password/i)).toBeVisible();
    await expect(page.locator('[data-testid="login-submit"]')).toBeEnabled();
  });
});
