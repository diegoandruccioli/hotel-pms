import { test, expect } from '@playwright/test';

// ---------------------------------------------------------------------------
// Mock data
// ---------------------------------------------------------------------------
const MOCK_USER = { username: 'admin', roles: ['ROLE_ADMIN'] };

const MOCK_GUEST = {
  id: 'g-001',
  firstName: 'Mario',
  lastName: 'Rossi',
  email: 'mario.rossi@example.com',
  phone: '3331234567',
  city: 'Roma',
  country: 'IT',
  identityDocuments: [],
  createdAt: '2026-01-01T10:00:00',
  updatedAt: '2026-01-01T10:00:00',
};

const MOCK_ROOMS = [
  { id: 'r-001', roomNumber: '101', type: 'SINGLE', status: 'CLEAN', pricePerNight: 80, active: true },
  { id: 'r-002', roomNumber: '102', type: 'DOUBLE', status: 'CLEAN', pricePerNight: 120, active: true },
];

const MOCK_RESERVATION = {
  id: 'res-001',
  guestId: MOCK_GUEST.id,
  guestFullName: 'Mario Rossi',
  checkInDate: '2026-04-01',
  checkOutDate: '2026-04-05',
  status: 'CONFIRMED',
  expectedGuests: 2,
  actualGuests: 0,
  lineItems: [
    { id: 'li-1', roomId: 'r-001', assigned: false, checkInDate: '2026-04-01', checkOutDate: '2026-04-05' },
  ],
  createdAt: '2026-03-18T12:00:00',
  updatedAt: '2026-03-18T12:00:00',
};

const MOCK_STAY = {
  id: 'stay-001',
  reservationId: MOCK_RESERVATION.id,
  guestId: MOCK_GUEST.id,
  roomId: 'r-001',
  status: 'CHECKED_IN',
  actualCheckInTime: '2026-04-01T14:00:00',
  guests: [
    {
      firstName: 'Mario',
      lastName: 'Rossi',
      gender: 'M',
      dateOfBirth: '1985-06-15',
      placeOfBirth: 'Roma',
      citizenship: 'IT',
      documentType: 'PASSPORT',
      documentNumber: 'AA1234567',
      documentPlaceOfIssue: 'Roma',
      isPrimaryGuest: true,
      travellerType: 'TOURIST',
      travelPurpose: 'LEISURE',
    },
  ],
};

// ---------------------------------------------------------------------------
// E2E: Book → Check-in
// ---------------------------------------------------------------------------
test.describe('Booking → Check-in Scenario', () => {
  test.beforeEach(async ({ page }) => {
    // Always authenticated
    await page.route('**/api/v1/auth/me', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_USER) })
    );
  });

  test('should create a reservation, then check-in a guest', async ({ page }) => {
    // ----- MOCK: guest search -----
    await page.route('**/api/v1/guests/search*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: [MOCK_GUEST], totalElements: 1, totalPages: 1, number: 0, size: 10 }),
      })
    );

    // ----- MOCK: rooms -----
    // Pattern must include ** suffix: getAllRooms() adds query params (?page=0&size=500&sort=...)
    // Response must be a SpringPage, not a plain array.
    await page.route('**/api/v1/rooms**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ content: MOCK_ROOMS, totalElements: MOCK_ROOMS.length, totalPages: 1, number: 0, size: 500 }),
      })
    );

    // ----- MOCK: reservation list + creation -----
    // Pattern must include ** suffix: getAllReservations() adds ?size=500
    // The GET is called twice:
    //   1. ReservationForm.loadInitialData() — must return empty to avoid double-booking conflict
    //   2. Reservations list page (after form redirect) — must return [MOCK_RESERVATION]
    let reservationsGetCount = 0;
    await page.route('**/api/v1/reservations**', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(MOCK_RESERVATION) });
      } else {
        reservationsGetCount += 1;
        const list = reservationsGetCount <= 2 ? [] : [MOCK_RESERVATION];
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ content: list, totalElements: list.length, totalPages: 1, number: 0, size: 500 }),
        });
      }
    });

    // ----- MOCK: single reservation -----
    await page.route('**/api/v1/reservations/res-001', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_RESERVATION) })
    );

    // ----- MOCK: guest by id -----
    await page.route('**/api/v1/guests/g-001', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_GUEST) })
    );

    // ----- MOCK: stays (for the reservation) -----
    await page.route('**/api/v1/stays/reservation/**', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) })
    );

    // ----- MOCK: stay creation (check-in) -----
    await page.route('**/api/v1/stays', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(MOCK_STAY) });
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ content: [MOCK_STAY], totalElements: 1, totalPages: 1, number: 0, size: 20 }),
        });
      }
    });

    // =====================================================================
    // STEP 1: Navigate to Reservations → New Reservation
    // =====================================================================
    await page.goto('/reservations');
    await expect(page).toHaveURL(/reservations/);

    // Click "New Reservation" — data-testid avoids locale-dependent text matching
    await page.locator('[data-testid="new-reservation-btn"]').click();
    await expect(page).toHaveURL(/reservations\/new/);

    // =====================================================================
    // STEP 2: Search & select a guest
    // =====================================================================
    const searchInput = page.getByPlaceholder(/search guest/i);
    await searchInput.fill('Mario');

    // Wait for search results and select
    await page.getByText('Mario Rossi').click();

    // Verify selected guest is shown
    await expect(page.getByRole('button', { name: /change/i })).toBeVisible();

    // =====================================================================
    // STEP 3: Fill reservation details
    // =====================================================================
    const dateInputs = page.locator('input[type="date"]');
    await dateInputs.nth(0).fill('2026-04-01');
    await dateInputs.nth(1).fill('2026-04-05');

    // Select a room
    await page.getByText('Room 101').click();

    // =====================================================================
    // STEP 4: Submit reservation
    // =====================================================================
    await page.getByRole('button', { name: /confirm reservation/i }).click();

    // Should redirect back to reservations list
    await expect(page).toHaveURL(/\/reservations$/, { timeout: 5000 });

    // =====================================================================
    // STEP 5: Navigate to Check-in from the reservation
    // =====================================================================
    // Click the Check-in button for the reservation row
    // The button is rendered as <button> (not <a>) in ReservationRow when status === 'CONFIRMED'
    await page.getByRole('button', { name: /check.?in/i }).first().click();

    // =====================================================================
    // STEP 6: Fill check-in guest form
    // =====================================================================
    await expect(page.getByText(/check-in/i)).toBeVisible({ timeout: 5000 });

    // Fill required guest fields
    const firstNameInput = page.getByLabel(/first name/i).first();
    if (await firstNameInput.isVisible()) {
      await firstNameInput.fill('Mario');
      await page.getByLabel(/last name/i).first().fill('Rossi');
      await page.getByLabel(/gender/i).first().fill('M');

      const dobInput = page.locator('input[type="date"]').first();
      await dobInput.fill('1985-06-15');

      await page.getByLabel(/place of birth/i).first().fill('Roma');
      await page.getByLabel(/citizenship/i).first().fill('IT');
      await page.getByLabel(/document type/i).first().fill('PASSPORT');
      await page.getByLabel(/document number/i).first().fill('AA1234567');
      await page.getByLabel(/document place/i).first().fill('Roma');
    }

    // =====================================================================
    // STEP 7: Submit check-in
    // =====================================================================
    await page.getByRole('button', { name: /complete check-in/i }).click();

    // Should redirect to stays list
    await expect(page).toHaveURL(/\/stays/, { timeout: 5000 });
  });
});
