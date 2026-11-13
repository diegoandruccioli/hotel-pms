import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'vitest-axe';
import { InvoiceActionsCell } from './InvoiceActionsCell';
import type { InvoiceResponse } from '../../types';

// One stable translator, like the real hook: a fresh function per render would re-run every memo.
const mockT = vi.hoisted(() =>
  vi.fn((key: string, opts?: { number?: string }) => (opts?.number ? `${key}:${opts.number}` : key)),
);
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: mockT, i18n: { language: 'en' } }),
}));

const invoice = (patch: Partial<InvoiceResponse> = {}): InvoiceResponse => ({
  id: 'inv-1', invoiceNumber: '2026/0221', issueDate: '2026-04-01T10:00:00', totalAmount: 100, status: 'ISSUED',
  documentType: 'FATTURA', sdiStatus: 'NOT_SENT', reservationId: 'r', guestId: 'g', payments: [], charges: [], ...patch,
});

const setup = (inv: InvoiceResponse) => {
  const onView = vi.fn();
  const onPay = vi.fn();
  const view = render(
    <InvoiceActionsCell invoice={inv} onView={onView} onPay={onPay} tView="view" tRegisterPayment="register_payment" />,
  );
  return { onView, onPay, ...view };
};

describe('InvoiceActionsCell', () => {
  it('names the row menu after the invoice number', () => {
    setup(invoice());
    expect(screen.getByRole('button', { name: 'invoice_actions_for:2026/0221' })).toBeInTheDocument();
  });

  it('falls back to the pending label for an invoice without a number', () => {
    setup(invoice({ invoiceNumber: '' }));
    expect(screen.getByRole('button', { name: 'invoice_actions_for:pending' })).toBeInTheDocument();
  });

  it('offers view and pay in the menu for an issued invoice and calls them', async () => {
    const user = userEvent.setup();
    const { onView, onPay } = setup(invoice());
    await user.click(screen.getByRole('button', { name: 'invoice_actions_for:2026/0221' }));
    expect(screen.getAllByRole('menuitem').map((m) => m.textContent)).toEqual(['visibilityview', 'paymentsregister_payment']);
    await user.click(screen.getByRole('menuitem', { name: 'register_payment' }));
    expect(onPay).toHaveBeenCalledTimes(1);
    expect(onView).not.toHaveBeenCalled();
  });

  it.each(['PAID', 'CANCELLED'] as const)('keeps one inline view link and no menu for a %s invoice', async (status) => {
    const user = userEvent.setup();
    const { onView } = setup(invoice({ status }));
    expect(screen.queryByRole('button', { name: /invoice_actions_for/ })).not.toBeInTheDocument();
    expect(screen.queryByText('register_payment')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'view' }));
    expect(onView).toHaveBeenCalledTimes(1);
  });

  it('has no accessibility violations', async () => {
    const { container } = setup(invoice());
    expect(await axe(container)).toHaveNoViolations();
  });
});
