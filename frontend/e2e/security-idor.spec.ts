import { test, expect } from '@playwright/test';

// ---------------------------------------------------------------------------
// Mock data
// ---------------------------------------------------------------------------
const MOCK_USER = { username: 'admin', role: 'ADMIN' };

const MOCK_GUEST = {
  id: 'g-hotel-a-001',
  firstName: 'Mario',
  lastName: 'Rossi',
  email: 'mario@example.com',
  phone: '+39 3331234567',
  city: 'Roma',
  country: 'IT',
  identityDocuments: [],
  createdAt: '2026-01-01T10:00:00',
  updatedAt: '2026-01-01T10:00:00',
};

// ---------------------------------------------------------------------------
// Test Suite: IDOR Prevention
// ---------------------------------------------------------------------------
test.describe('Security – IDOR Prevention', () => {

  test.beforeEach(async ({ page }) => {
    // All tests run as an authenticated admin belonging to hotel A
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_USER),
      })
    );
  });

  // -------------------------------------------------------------------------
  // T-IDOR-SEC-01
  // Attack path: cross-hotel guest write attempt.
  // A user from hotel A opens the edit form for a guest that actually belongs
  // to hotel B (the UUID was obtained via enumeration or prior data leak).
  // The backend enforces hotel_id scoping via findByIdAndHotelId and returns
  // 404 Not Found.
  // Expected: a toast error appears; the modal stays open (no silent success);
  // the guest list remains unchanged (no phantom data).
  // -------------------------------------------------------------------------
  test('T-IDOR-SEC-01: cross-hotel guest edit rejected (404) → toast error, modal stays open', async ({ page }) => {
    // Route all /guests traffic through a single handler to control GET vs PUT
    await page.route('**/api/v1/guests**', async (route) => {
      const method = route.request().method();
      const url = route.request().url();

      if (method === 'GET' && !url.includes(`/guests/${MOCK_GUEST.id}`)) {
        // GET /api/v1/guests — return list with the mock guest
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            content: [MOCK_GUEST],
            totalElements: 1,
            totalPages: 1,
            number: 0,
            size: 20,
          }),
        });
      } else if (method === 'PUT') {
        // PUT /api/v1/guests/{id} — backend rejects: guest belongs to another hotel
        await route.fulfill({
          status: 404,
          contentType: 'application/json',
          body: JSON.stringify({ detail: 'GUEST_NOT_FOUND' }),
        });
      } else {
        await route.fallback();
      }
    });

    await page.goto('/guests');
    await expect(page).toHaveURL(/\/guests/);

    // The guest row is visible — click Edit to open the form
    await page.getByRole('button', { name: /edit/i }).first().click();
    await expect(page.locator('[role="dialog"]')).toBeVisible({ timeout: 5_000 });

    // Modify a field (simulates attacker attempting to overwrite data)
    await page.locator('input[name="city"]').fill('Attacker City');

    // Submit → PUT /api/v1/guests/{id} → 404 (hotel_id scope enforcement)
    await page.locator('button[form="guest-form"]').click();

    // A toast error must appear (role=alert from ToastContainer)
    await expect(page.getByRole('alert')).toBeVisible({ timeout: 5_000 });

    // The modal must remain open — the save did NOT succeed silently
    await expect(page.locator('[role="dialog"]')).toBeVisible();
  });

  // -------------------------------------------------------------------------
  // T-IDOR-SEC-02
  // Attack path: direct URL manipulation to access a cross-hotel reservation.
  // An attacker crafts the URL /reservations/edit/{uuid-from-hotel-B} and
  // navigates to it directly.  The backend returns 404 (findByIdAndHotelId).
  // Expected: the form shows an error state and no reservation data is
  // rendered — the attacker cannot read or modify hotel B's booking.
  // -------------------------------------------------------------------------
  test('T-IDOR-SEC-02: cross-hotel reservation edit via URL manipulation → error state, no data shown', async ({ page }) => {
    const CROSS_HOTEL_RES_ID = 'res-hotel-b-99999';

    // General reservations list — returns empty (no cross-hotel data)
    await page.route('**/api/v1/reservations**', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 500 }),
        });
      } else {
        await route.fallback();
      }
    });

    // Specific reservation lookup — returns 404 (cross-hotel IDOR block)
    // Registered after the general route so Playwright tries it first (LIFO)
    await page.route(`**/api/v1/reservations/${CROSS_HOTEL_RES_ID}`, (route) =>
      route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({ detail: 'RESERVATION_NOT_FOUND' }),
      })
    );

    await page.route('**/api/v1/rooms**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 500 }),
      })
    );

    // Navigate directly to the cross-hotel reservation edit URL
    await page.goto(`/reservations/edit/${CROSS_HOTEL_RES_ID}`);

    // The form must display an error (loadInitialData catches the 404 and sets error state)
    const errorBox = page.locator('.bg-error-container').first();
    await expect(errorBox).toBeVisible({ timeout: 10_000 });

    // Date fields must be empty — no reservation data was populated from another hotel
    const dateInputs = page.locator('input[type="date"]');
    if (await dateInputs.count() > 0) {
      await expect(dateInputs.first()).toHaveValue('');
    }
  });

  // -------------------------------------------------------------------------
  // T-IDOR-SEC-03
  // Verification: the guest list is hotel-scoped.
  // The API returns only the current hotel's guests; the frontend renders
  // exactly those entries and no phantom rows from other hotels are injected.
  // -------------------------------------------------------------------------
  test('T-IDOR-SEC-03: guest list renders only current-hotel guests (hotel_id scoping)', async ({ page }) => {
    const hotelAGuests = [
      { ...MOCK_GUEST, id: 'g-hotel-a-001', firstName: 'AliceHotelA', lastName: 'Smith' },
      { ...MOCK_GUEST, id: 'g-hotel-a-002', firstName: 'BobHotelA', lastName: 'Jones' },
    ];

    await page.route('**/api/v1/guests', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: hotelAGuests,
          totalElements: 2,
          totalPages: 1,
          number: 0,
          size: 20,
        }),
      })
    );

    await page.goto('/guests');
    await expect(page).toHaveURL(/\/guests/);

    // Both hotel-A guests must be visible
    await expect(page.getByText('AliceHotelA Smith')).toBeVisible({ timeout: 5_000 });
    await expect(page.getByText('BobHotelA Jones')).toBeVisible({ timeout: 5_000 });

    // Exactly 2 Edit buttons — no phantom rows from other hotels
    await expect(page.getByRole('button', { name: /edit/i })).toHaveCount(2);
  });
});
