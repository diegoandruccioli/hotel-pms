import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { PaymentModal } from './PaymentModal';
import { billingService } from '../../services/billingService';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/billingService', () => ({
  billingService: { processPayment: vi.fn() },
}));

vi.mock('../../store/toastStore', () => ({
  useToastStore: (sel: unknown) =>
    (sel as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

vi.mock('../../components/m3/M3Dialog', () => ({
  M3Dialog: ({ children, title }: { children: React.ReactNode; title: string }) => (
    <div role="dialog" aria-label={title}>{children}</div>
  ),
}));

const INVOICE = {
  id: 'inv1', invoiceNumber: 'INV-001', issueDate: '2026-01-01T00:00:00',
  totalAmount: 150, status: 'ISSUED' as const, documentType: 'FATTURA' as const, sdiStatus: 'NOT_SENT' as const,
  reservationId: 'res1', guestId: 'g1', stayId: 's1', payments: [], charges: [],
};

const PAYMENT_RESPONSE = {
  id: 'pay1', paymentDate: '2026-01-01T12:00:00', amount: 150,
  paymentMethod: 'CASH', transactionReference: 'TXN-001', invoiceId: 'inv1',
};

describe('PaymentModal', () => {
  const onClose = vi.fn();
  const onPaid = vi.fn();

  beforeEach(() => vi.clearAllMocks());

  it('renders invoice details', () => {
    render(<PaymentModal invoice={INVOICE} onClose={onClose} onPaid={onPaid} />);
    expect(screen.getByText('INV-001')).toBeInTheDocument();
  });

  it('pre-fills amount from invoice totalAmount', () => {
    render(<PaymentModal invoice={INVOICE} onClose={onClose} onPaid={onPaid} />);
    const amountInput = screen.getByDisplayValue('150') as HTMLInputElement;
    expect(amountInput).toBeInTheDocument();
  });

  it('shows validation error for invalid amount', async () => {
    render(<PaymentModal invoice={INVOICE} onClose={onClose} onPaid={onPaid} />);
    const amountInput = screen.getByDisplayValue('150');
    fireEvent.change(amountInput, { target: { value: '-5' } });
    fireEvent.submit(document.querySelector('form')!);
    await waitFor(() => expect(screen.getByText('error_invalid_amount')).toBeInTheDocument());
  });

  it('calls processPayment and onPaid on successful submission', async () => {
    vi.mocked(billingService.processPayment).mockResolvedValue(PAYMENT_RESPONSE as never);
    render(<PaymentModal invoice={INVOICE} onClose={onClose} onPaid={onPaid} />);
    fireEvent.submit(document.querySelector('form')!);
    await waitFor(() => expect(onPaid).toHaveBeenCalledOnce());
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('calls onClose when cancel clicked', () => {
    render(<PaymentModal invoice={INVOICE} onClose={onClose} onPaid={onPaid} />);
    fireEvent.click(screen.getByText('cancel'));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('renders all payment method options', () => {
    render(<PaymentModal invoice={INVOICE} onClose={onClose} onPaid={onPaid} />);
    expect(screen.getByText('payment_method_cash')).toBeInTheDocument();
    expect(screen.getByText('payment_method_credit_card')).toBeInTheDocument();
  });

  it('passes axe accessibility check', async () => {
    const { container } = render(<PaymentModal invoice={INVOICE} onClose={onClose} onPaid={onPaid} />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
