import { test, expect } from '@playwright/test';
import { ConsoleGuard } from './support/consoleGuard';

// Blocco 3 (partial) — Calendar / Housekeeping / Rates element sweep.
// Built incrementally: this file starts with the regression test for the
// housekeeping status-count bug found via Chrome MCP on 2026-08-27, then
// grows to cover the rest of this area's interactive elements.
//
// FilterBadge (Housekeeping.tsx:327-351) renders each summary count as its
// OWN <button> (not a <div>) inside `<div className="grid grid-cols-3
// gap-4">` — scope on that exact structure, not on card-level "-> Pulita"
// transition buttons which share overlapping text.
test.describe('Blocco 3 — Housekeeping', () => {
  test('status-count badges update after a single status change, without a manual page reload', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/housekeeping');
    await page.getByRole('heading', { name: /pulizie|housekeeping/i }).waitFor();
    guard.checkpoint('housekeeping loaded');

    const badges = page.locator('.grid.grid-cols-3.gap-4 > button');
    await expect(badges).toHaveCount(3);
    const cleanBadge = badges.nth(0); // ALL_STATUSES = ['CLEAN', 'DIRTY', 'MAINTENANCE']
    const before = Number((await cleanBadge.locator('p').first().textContent())?.trim());
    expect(Number.isFinite(before)).toBe(true);

    // `.lg\\:grid-cols-4` is the room-card grid's own unique class (the badge
    // grid above it is `grid-cols-3`, no lg: variant) — direct children (`>
    // div`) are exactly the RoomCards, so `.filter({has})` can't match an
    // outer wrapper the way an unscoped `div` locator did.
    const roomCards = page.locator('.lg\\:grid-cols-4 > div');
    const dirtyCard = roomCards.filter({ has: page.getByRole('button', { name: /^→ pulita$|^→ clean$/i }) }).first();
    await expect(dirtyCard).toBeVisible();
    await dirtyCard.getByRole('button', { name: /^→ pulita$|^→ clean$/i }).click();
    await page.getByText(/stato camera|room updated to/i).waitFor({ timeout: 5000 });
    guard.checkpoint('status changed, toast shown');

    // No manual reload — this is the whole point of the regression: the
    // badge must reflect the mutation on its own via query invalidation.
    await expect(async () => {
      const after = Number((await cleanBadge.locator('p').first().textContent())?.trim());
      expect(after, `CLEAN badge still reads ${after} after marking a room clean (was ${before})`).toBe(before + 1);
    }).toPass({ timeout: 5000 });
    guard.checkpoint('CLEAN badge incremented without manual refresh');
  });

  test('status-count badges update after the explicit "Aggiorna" button too', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/housekeeping');
    await page.getByRole('heading', { name: /pulizie|housekeeping/i }).waitFor();

    const badges = page.locator('.grid.grid-cols-3.gap-4 > button');
    await expect(badges).toHaveCount(3);
    const dirtyBadge = badges.nth(1);
    const before = Number((await dirtyBadge.locator('p').first().textContent())?.trim());
    expect(Number.isFinite(before)).toBe(true);

    const roomCards = page.locator('.lg\\:grid-cols-4 > div');
    const cleanCard = roomCards.filter({ has: page.getByRole('button', { name: /^→ sporca$|^→ dirty$/i }) }).first();
    await expect(cleanCard).toBeVisible();
    await cleanCard.getByRole('button', { name: /^→ sporca$|^→ dirty$/i }).click();
    await page.getByText(/stato camera|room updated to/i).waitFor({ timeout: 5000 });

    await page.getByRole('button', { name: /aggiorna|refresh/i }).click();
    await expect(async () => {
      const after = Number((await dirtyBadge.locator('p').first().textContent())?.trim());
      expect(after).toBe(before + 1);
    }).toPass({ timeout: 5000 });
    guard.checkpoint('DIRTY badge correct after explicit refresh');
  });
});
