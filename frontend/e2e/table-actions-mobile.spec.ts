import { test, expect, type Page } from '@playwright/test';
import { mockAuthMe, mockServerEvents, mockUnhandledApi } from './fixtures/mockApi';
import { GUESTS, RESERVATIONS, ROOMS, springPage } from '../e2e-visual/fixtures/data';

// On a phone the row actions used to sit past the right edge of a sideways-scrolling table.
// The last column is now pinned, so the row-actions trigger must be on screen without scrolling.
const PHONE = { width: 390, height: 844 };

const QUOTATIONS = [
  {
    id: 'q-1', guestFullName: 'Mario Rossi', checkInDate: '2026-11-10', checkOutDate: '2026-11-12',
    options: [{ totalPrice: 320 }], totalPrice: 320, validUntil: '2026-11-01', status: 'DRAFT', sendFailed: false,
  },
];

const json = (page: Page, url: string | RegExp, body: unknown) =>
  page.route(url, (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) }),
  );

const SCREENS = [
  {
    name: 'Reservations',
    path: '/reservations',
    mock: async (page: Page) => {
      await json(page, /\/api\/v1\/reservations/, springPage(RESERVATIONS));
      await json(page, /\/api\/v1\/rooms/, springPage(ROOMS, 200));
    },
    wait: 'Mario Rossi',
  },
  {
    name: 'Guests',
    path: '/guests',
    mock: (page: Page) => json(page, /\/api\/v1\/guests/, springPage(GUESTS)),
    wait: 'Mario',
  },
  {
    name: 'Quotations',
    path: '/quotations',
    mock: (page: Page) => json(page, /\/api\/v1\/quotations/, springPage(QUOTATIONS)),
    wait: 'Mario Rossi',
  },
];

test.describe('Row actions on a phone', () => {
  test.use({ viewport: PHONE });

  for (const screen of SCREENS) {
    test(`${screen.name}: the row-actions trigger is on screen and opens its menu`, async ({ page }) => {
      await mockUnhandledApi(page);
      await mockServerEvents(page);
      await mockAuthMe(page);
      await screen.mock(page);

      await page.goto(screen.path);
      await expect(page.getByText(screen.wait).first()).toBeVisible({ timeout: 10000 });

      const trigger = page.getByRole('button', { name: /^(Actions for|Azioni per) / }).first();
      await expect(trigger).toBeVisible();
      const box = await trigger.boundingBox();
      expect(box).not.toBeNull();
      // Fully inside the viewport, with no sideways scrolling needed.
      expect(box!.x).toBeGreaterThanOrEqual(0);
      expect(box!.x + box!.width).toBeLessThanOrEqual(PHONE.width);

      await trigger.click();
      await expect(page.getByRole('menuitem').first()).toBeVisible();
    });
  }
});
