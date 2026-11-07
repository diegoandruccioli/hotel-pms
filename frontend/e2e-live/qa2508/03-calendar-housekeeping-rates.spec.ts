import { test, expect, type Page } from '@playwright/test';
import { ConsoleGuard } from './support/consoleGuard';

// Blocco 3 (partial) — Calendar / Housekeeping / Rates element sweep.
// Built incrementally: this file starts with the regression test for the
// housekeeping status-count bug found via Chrome MCP on 2026-08-27, then
// grows to cover the rest of this area's interactive elements.
//
// Housekeeping is a kanban: one `role="group"` column per status, named by its
// <h2> (Sporca / Pulita / Manutenzione / Occupata), holding one RoomCard each
// (marked by `data-testid="room-card-tone"`). The column's room count is what
// the old summary badges used to show, so that is what these tests read.
const CLEAN = /^pulita$|^clean$/i;
const DIRTY = /^sporca$|^dirty$/i;
const column = (page: Page, name: RegExp) => page.getByRole('group', { name });
const roomsIn = (page: Page, name: RegExp) => column(page, name).getByTestId('room-card-tone');

test.describe('Blocco 3 — Housekeeping', () => {
  test('status-count badges update after a single status change, without a manual page reload', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/housekeeping');
    await page.getByRole('heading', { name: /pulizie|housekeeping/i }).waitFor();
    guard.checkpoint('housekeeping loaded');

    // Wait for the room cards: the columns render empty placeholders while the query loads.
    await expect(page.getByTestId('room-card-tone').first()).toBeVisible();
    const before = await roomsIn(page, CLEAN).count();

    const toClean = column(page, DIRTY).getByRole('button', { name: /^→ pulita$|^→ clean$/i }).first();
    await expect(toClean).toBeVisible();
    await toClean.click();
    await page.getByText(/stato camera|room updated to/i).waitFor({ timeout: 5000 });
    guard.checkpoint('status changed, toast shown');

    // No manual reload — the Clean column must reflect the mutation on its own via query invalidation.
    await expect(roomsIn(page, CLEAN)).toHaveCount(before + 1);
    guard.checkpoint('Clean column incremented without manual refresh');
  });

  test('status-count badges update after the explicit "Aggiorna" button too', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/housekeeping');
    await page.getByRole('heading', { name: /pulizie|housekeeping/i }).waitFor();

    await expect(page.getByTestId('room-card-tone').first()).toBeVisible();
    const before = await roomsIn(page, DIRTY).count();

    const toDirty = column(page, CLEAN).getByRole('button', { name: /^→ sporca$|^→ dirty$/i }).first();
    await expect(toDirty).toBeVisible();
    await toDirty.click();
    await page.getByText(/stato camera|room updated to/i).waitFor({ timeout: 5000 });

    await page.getByRole('button', { name: /aggiorna|refresh/i }).click();
    await expect(roomsIn(page, DIRTY)).toHaveCount(before + 1);
    guard.checkpoint('Dirty column correct after explicit refresh');
  });
});
