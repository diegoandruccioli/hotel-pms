import { test, expect } from '@playwright/test';

// ---------------------------------------------------------------------------
// Helper: mock all APIs consumed by the Dashboard page (/) to avoid
// network errors when unauthorized users are redirected to the root.
// ---------------------------------------------------------------------------
async function mockDashboardApis(page: import('@playwright/test').Page): Promise<void> {
  const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
  await page.route('**/api/v1/guests/search**', (route) =>
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
// Test Suite: RBAC Enforcement
// ---------------------------------------------------------------------------
test.describe('Security – RBAC Enforcement', () => {

  // -------------------------------------------------------------------------
  // T-RBAC-SEC-01
  // Attack path: a RECEPTIONIST navigates directly to /owner-dashboard.
  // Expected: ProtectedRoute (allowedRoles=['OWNER','ADMIN']) detects the
  // insufficient role and redirects to /.  The Owner Dashboard content must
  // never be rendered for a RECEPTIONIST, even on direct URL access.
  // -------------------------------------------------------------------------
  test('T-RBAC-SEC-01: RECEPTIONIST accessing /owner-dashboard → ProtectedRoute redirects to /', async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ username: 'receptionist', role: 'RECEPTIONIST' }),
      })
    );
    await mockDashboardApis(page);

    await page.goto('/owner-dashboard');

    // ProtectedRoute must redirect the insufficient role to / (full URL ends with the root path)
    await expect(page).toHaveURL(/\/$/, { timeout: 10_000 });

    // The Owner Dashboard heading must never have been rendered
    await expect(page.getByRole('heading', { name: /owner dashboard/i })).not.toBeVisible();
  });

  // -------------------------------------------------------------------------
  // T-RBAC-SEC-02
  // Access control positive test: ADMIN has the OWNER_ADMIN_ROLES grant
  // and must be able to reach /owner-dashboard without any redirect.
  // -------------------------------------------------------------------------
  test('T-RBAC-SEC-02: ADMIN accessing /owner-dashboard → access granted', async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ username: 'admin', role: 'ADMIN' }),
      })
    );
    await mockDashboardApis(page);

    await page.goto('/owner-dashboard');

    // URL must NOT change — no redirect
    await expect(page).toHaveURL(/\/owner-dashboard/, { timeout: 10_000 });

    // Owner Dashboard heading must be visible (confirms page rendered, not redirected)
    await expect(page.getByRole('heading', { name: /owner dashboard/i })).toBeVisible({ timeout: 10_000 });
  });

  // -------------------------------------------------------------------------
  // T-RBAC-SEC-03
  // Access control positive test: OWNER must also have access to
  // /owner-dashboard — same OWNER_ADMIN_ROLES grant as ADMIN.
  // -------------------------------------------------------------------------
  test('T-RBAC-SEC-03: OWNER accessing /owner-dashboard → access granted', async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ username: 'owner_user', role: 'OWNER' }),
      })
    );
    await mockDashboardApis(page);

    await page.goto('/owner-dashboard');

    await expect(page).toHaveURL(/\/owner-dashboard/, { timeout: 10_000 });
    await expect(page.getByRole('heading', { name: /owner dashboard/i })).toBeVisible({ timeout: 10_000 });
  });

  // -------------------------------------------------------------------------
  // T-RBAC-SEC-04
  // Attack path: unauthenticated direct navigation to a protected route.
  // Expected: ProtectedRoute (no user) redirects to /login before any
  // protected content is rendered.
  // -------------------------------------------------------------------------
  test('T-RBAC-SEC-04: unauthenticated direct navigation to protected route → /login', async ({ page }) => {
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Unauthorized' }),
      })
    );

    // Navigate directly to a protected route without a session
    await page.goto('/billing');

    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 });
    await expect(page.locator('[data-testid="login-form"]')).toBeVisible();
  });
});
