import { test, expect } from '@playwright/test';

// ---------------------------------------------------------------------------
// Mock data
// ---------------------------------------------------------------------------
const MOCK_USER = { username: 'admin', role: 'ADMIN' };

// ---------------------------------------------------------------------------
// Helper: mock all APIs consumed by the Dashboard page (/) so that redirects
// to the root do not produce noisy network errors or timing issues.
// ---------------------------------------------------------------------------
async function mockDashboardApis(page: import('@playwright/test').Page): Promise<void> {
  const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
  await page.route('**/api/v1/guests', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(emptyPage) })
  );
  await page.route('**/api/v1/reservations**', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(emptyPage) });
    } else {
      await route.fallback();
    }
  });
  await page.route('**/api/v1/stays', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(emptyPage) })
  );
  await page.route('**/api/v1/rooms**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: [], totalElements: 1, totalPages: 1, number: 0, size: 100 }),
    })
  );
  await page.route('**/api/v1/reports/owner**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ invoices: [], startDate: '2000-01-01', endDate: '2099-12-31' }),
    })
  );
}

// ---------------------------------------------------------------------------
// Test Suite: Authentication Attack Paths
// ---------------------------------------------------------------------------
test.describe('Security – Authentication Attack Paths', () => {

  // -------------------------------------------------------------------------
  // T-AUTH-SEC-01
  // Attack path: unauthenticated request to a protected route.
  // Expected: ProtectedRoute redirects to /login before any protected content
  // is rendered.  No sensitive data is ever visible to the unauthenticated user.
  // -------------------------------------------------------------------------
  test('T-AUTH-SEC-01: unauthenticated request to protected route → redirect to /login', async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Unauthorized' }),
      })
    );

    // Navigate directly to a protected route without a valid session
    await page.goto('/guests');

    // Must land on the login page — protected content must not be visible
    await expect(page).toHaveURL(/\/login/);
    await expect(page.locator('[data-testid="login-form"]')).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // T-AUTH-SEC-02
  // Attack path: session expires while the user is actively browsing.
  // Flow: GET /guests → 401 → Axios interceptor tries POST /refresh → 401 →
  // performLogout() → window.location.href = '/login'.
  // Expected: user is silently logged out and redirected to /login.
  // -------------------------------------------------------------------------
  test('T-AUTH-SEC-02: expired session (401 on data + failed refresh) → performLogout → /login', async ({ page }) => {
    let meCallCount = 0;

    // First /me → 200 (app startup succeeds). Subsequent calls return 401.
    await page.route('**/api/v1/auth/me', async (route) => {
      meCallCount += 1;
      await route.fulfill({
        status: meCallCount === 1 ? 200 : 401,
        contentType: 'application/json',
        body: meCallCount === 1
          ? JSON.stringify(MOCK_USER)
          : JSON.stringify({ error: 'Unauthorized' }),
      });
    });

    // Refresh endpoint also returns 401 — refresh token has expired / been revoked
    await page.route('**/api/v1/auth/refresh', (route) =>
      route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Unauthorized' }),
      })
    );

    // GET /guests returns 401 → triggers the silent-refresh → that also fails
    await page.route('**/api/v1/guests', (route) =>
      route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Unauthorized' }),
      })
    );

    await page.goto('/guests');

    // After the failed refresh, performLogout() sets window.location.href = '/login'
    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 });
  });

  // -------------------------------------------------------------------------
  // T-AUTH-SEC-03
  // Attack path: brute-force login — gateway rate-limits the IP after repeated
  // failures and returns 429 Too Many Requests.
  // Expected: the frontend shows a generic error; it must NOT expose account
  // details (lock status, remaining attempts) that could help an attacker.
  // The submit button must re-enable (UI-level lockout would confuse users).
  // -------------------------------------------------------------------------
  test('T-AUTH-SEC-03: brute-force rate limit (429) → generic error, no account detail exposed', async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Unauthorized' }),
      })
    );

    // Login endpoint responds with rate-limit status
    await page.route('**/api/v1/auth/login', (route) =>
      route.fulfill({
        status: 429,
        contentType: 'application/json',
        body: JSON.stringify({ detail: 'Too Many Requests' }),
      })
    );

    await page.goto('/login');
    await expect(page.locator('[data-testid="login-form"]')).toBeVisible();

    await page.getByRole('textbox', { name: /username/i }).fill('admin');
    await page.getByRole('textbox', { name: /password/i }).fill('wrongpassword');
    await page.locator('[data-testid="login-submit"]').click();

    // A generic error must be shown — no wording that reveals lock/attempt details
    // Target specifically the error container paragraph (not the field labels or button)
    const errorParagraph = page.locator('[data-testid="login-form"] .bg-error-container p');
    await expect(errorParagraph).toBeVisible({ timeout: 5_000 });

    const errorContent = await errorParagraph.textContent();
    // Must not mention locking, banning, or remaining attempts — that would aid attackers
    expect(errorContent).not.toMatch(/lock|ban|remain|attempt/i);

    // The submit button must re-enable after error (no permanent UI-level lockout)
    await expect(page.locator('[data-testid="login-submit"]')).toBeEnabled({ timeout: 5_000 });
  });

  // -------------------------------------------------------------------------
  // T-AUTH-SEC-04
  // Attack path: CSRF token absent or mismatched.
  // The api-gateway CsrfFilter returns 403 Forbidden on every mutating request
  // that lacks a valid X-CSRF-Token header matching the csrf_token cookie.
  // Expected: a toast error is shown; the modal does NOT close (no silent
  // success); no phantom data is injected into the guest list.
  // -------------------------------------------------------------------------
  test('T-AUTH-SEC-04: CSRF rejection (403 on POST mutation) → toast error, no silent success', async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_USER),
      })
    );

    await mockDashboardApis(page);

    // Override /guests: GET returns empty list; POST (create) rejected with 403
    await page.route('**/api/v1/guests', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
        });
      } else {
        // CSRF mismatch — gateway rejects the mutation
        await route.fulfill({
          status: 403,
          contentType: 'application/json',
          body: JSON.stringify({ detail: 'Forbidden' }),
        });
      }
    });

    await page.goto('/guests');
    await expect(page).toHaveURL(/\/guests/);

    // Open the "Add Guest" modal
    await page.getByRole('button', { name: /add guest/i }).click();
    await expect(page.locator('[role="dialog"]')).toBeVisible({ timeout: 5_000 });

    // Fill the form with minimal valid data
    await page.locator('input[name="firstName"]').fill('Test');
    await page.locator('input[name="lastName"]').fill('Guest');
    await page.locator('input[name="email"]').fill('test@example.com');

    // Submit → triggers POST /api/v1/guests → 403 (CSRF)
    await page.locator('button[form="guest-form"]').click();

    // A toast error alert (role=alert) must appear
    await expect(page.getByRole('alert')).toBeVisible({ timeout: 5_000 });

    // The modal must remain open — no silent success
    await expect(page.locator('[role="dialog"]')).toBeVisible();
  });
});
