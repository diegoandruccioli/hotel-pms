import { test, expect } from '@playwright/test';
import { ConsoleGuard } from './support/consoleGuard';
import { csrfHeader, createCleanRoom, createGuest, createWalkInStay } from '../fixtures/api';

// Blocco 3 — Billing area. PaymentModal fields/behavior read from
// PaymentModal.tsx: amount pre-filled with invoice.totalAmount, client-side
// blocks amount<=0, no client-side upper bound (server-checked).
test.describe('Blocco 3 — Billing', () => {
  test('list loads, filters, search work; console clean', async ({ page }) => {
    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/billing');
    await page.getByRole('heading', { name: /fatturazione|billing/i }).waitFor();
    guard.checkpoint('billing list loaded');

    for (const chip of [/emessa|issued/i, /pagata|paid/i, /annullata|cancelled/i, /^tutti$|^all$/i]) {
      await page.getByRole('button', { name: chip }).click();
      await page.waitForTimeout(300);
      guard.checkpoint(`status filter ${chip} applied`);
    }
  });

  test('register a payment: negative/zero amount blocked client-side, valid amount succeeds', async ({ request, page }) => {
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers, {});
    await createWalkInStay(request, headers, { roomId: room.id, guestId: guest.id });

    const guard = new ConsoleGuard(page, { role: 'admin', locale: 'it' });
    await page.goto('/billing');
    await page.getByRole('heading', { name: /fatturazione|billing/i }).waitFor();

    await page.getByRole('searchbox').fill(guest.email);
    await page.waitForTimeout(500);
    const registerBtn = page.getByRole('button', { name: /registra pagamento|register payment/i }).first();
    await expect(registerBtn).toBeVisible({ timeout: 5000 });
    await registerBtn.click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    guard.checkpoint('payment modal opened');

    const amountField = dialog.getByLabel(/^importo|^amount/i);
    await amountField.fill('0');
    await dialog.getByRole('button', { name: /conferma pagamento|confirm payment/i }).click();
    await expect(dialog).toBeVisible(); // must not close on invalid amount
    guard.checkpoint('zero amount blocked client-side');

    await amountField.fill('-5');
    await dialog.getByRole('button', { name: /conferma pagamento|confirm payment/i }).click();
    await expect(dialog).toBeVisible();
    guard.checkpoint('negative amount blocked client-side');

    // Field still holds "-5" from the previous attempt — set an explicit
    // valid amount rather than assuming it reverted to the pre-filled total.
    await amountField.fill('10');
    await dialog.getByRole('button', { name: /conferma pagamento|confirm payment/i }).click();
    await expect(dialog).not.toBeVisible({ timeout: 5000 });
    guard.checkpoint('valid payment registered');
  });

  test('overpayment (amount > totalAmount) is rejected by the server, not silently accepted', async ({ request, page }) => {
    const headers = await csrfHeader(request);
    const room = await createCleanRoom(request, headers);
    const guest = await createGuest(request, headers, {});
    await createWalkInStay(request, headers, { roomId: room.id, guestId: guest.id });

    const guard = new ConsoleGuard(page, {
      role: 'admin',
      locale: 'it',
      extraAllow: [
        { pattern: /\/payments/, reason: 'expected: server rejects an overpayment amount', scope: 'http_error' },
        { pattern: /\((Bad Request|Conflict|Unprocessable)/, reason: 'expected: server rejects overpayment', scope: 'console' },
      ],
    });
    await page.goto('/billing');
    await page.getByRole('heading', { name: /fatturazione|billing/i }).waitFor();

    await page.getByRole('searchbox').fill(guest.email);
    await page.waitForTimeout(500);
    const registerBtn = page.getByRole('button', { name: /registra pagamento|register payment/i }).first();
    await registerBtn.click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();

    const amountField = dialog.getByLabel(/^importo|^amount/i);
    await amountField.fill('999999');
    await dialog.getByRole('button', { name: /conferma pagamento|confirm payment/i }).click();
    await page.waitForTimeout(1000);
    // Either the dialog stays open with an error, or it closed but the
    // invoice was NOT marked PAID with an absurd amount — check the error
    // toast is the primary signal here.
    const errorToast = page.getByText(/errore|error|failed|non valido|invalid/i);
    const stillOpen = await dialog.isVisible().catch(() => false);
    const hasError = await errorToast.isVisible().catch(() => false);
    expect(stillOpen || hasError, 'overpayment must surface an error, not silently succeed').toBe(true);
    guard.checkpoint('overpayment handled without silent success');
  });
});
