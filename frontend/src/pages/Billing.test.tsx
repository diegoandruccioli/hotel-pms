import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'vitest-axe';
import { Billing } from './Billing';
import { billingService } from '../services/billingService';
import type { InvoiceResponse, InvoiceSearchResult } from '../types/billing.types';

vi.mock('react-i18next', () => {
  const t = vi.fn((key: string) => key);
  return {
    useTranslation: () => ({ t, i18n: { language: 'en' } }),
    initReactI18next: { type: '3rdParty', init: vi.fn() },
  };
});

vi.mock('../services/billingService', () => ({
  billingService: {
    searchInvoices: vi.fn(),
    processPayment: vi.fn(),
  },
}));

vi.mock('../store/toastStore', () => ({
  useToastStore: (sel: (s: { addToast: () => void }) => unknown) =>
    sel({ addToast: vi.fn() }),
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const ISSUED_INVOICE: InvoiceResponse = {
  id: 'inv-1',
  invoiceNumber: 'INV-001',
  issueDate: '2026-04-01T10:00:00',
  totalAmount: 150,
  status: 'ISSUED',
  documentType: 'FATTURA',
  sdiStatus: 'NOT_SENT' as const,
  reservationId: 'res-1',
  guestId: 'guest-1',
  payments: [],
  charges: [
    { id: 'ch-1', type: 'FB_ORDER', description: 'Dinner', amount: 50 },
  ],
};

const PAID_INVOICE: InvoiceResponse = {
  ...ISSUED_INVOICE,
  id: 'inv-2',
  invoiceNumber: 'INV-002',
  status: 'PAID',
  payments: [
    {
      id: 'pay-1',
      paymentDate: '2026-04-02T12:00:00',
      amount: 150,
      paymentMethod: 'CASH',
      invoiceId: 'inv-2',
    },
  ],
};

const page = (results: InvoiceSearchResult[], totalPages = 1) =>
  ({ content: results, totalPages, totalElements: results.length });

const result = (invoice: InvoiceResponse, guestName: string | null = null): InvoiceSearchResult =>
  ({ invoice, guestName });

describe('Billing', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should show loading spinner initially', () => {
    vi.mocked(billingService.searchInvoices).mockReturnValue(new Promise(() => {}));
    render(<Billing />);
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should render invoices on success', async () => {
    vi.mocked(billingService.searchInvoices).mockResolvedValueOnce(page([result(ISSUED_INVOICE)]) as never);
    render(<Billing />);
    await waitFor(() => expect(screen.getByText('INV-001')).toBeInTheDocument());
    expect(billingService.searchInvoices).toHaveBeenCalledWith({
      status: undefined,
      query: '',
      dateFrom: undefined,
      dateTo: undefined,
      page: 0,
      size: 20,
    });
  });

  it('should render the resolved guest name for a result', async () => {
    vi.mocked(billingService.searchInvoices)
        .mockResolvedValueOnce(page([result(ISSUED_INVOICE, 'Mario Rossi')]) as never);
    render(<Billing />);
    await waitFor(() => expect(screen.getByText('Mario Rossi')).toBeInTheDocument());
  });

  it('should show empty state when no invoices', async () => {
    vi.mocked(billingService.searchInvoices).mockResolvedValueOnce(page([]) as never);
    render(<Billing />);
    await waitFor(() => expect(screen.getByText('no_invoices')).toBeInTheDocument());
  });

  it('should show error on failure', async () => {
    vi.mocked(billingService.searchInvoices).mockRejectedValueOnce(new Error('Network error'));
    render(<Billing />);
    await waitFor(() => expect(screen.getByText('error_loading_invoices')).toBeInTheDocument());
  });

  it('should not show register_payment button for PAID invoice', async () => {
    vi.mocked(billingService.searchInvoices).mockResolvedValueOnce(page([result(PAID_INVOICE)]) as never);
    render(<Billing />);
    await waitFor(() => expect(screen.getByText('INV-002')).toBeInTheDocument());
    expect(screen.queryByText('register_payment')).not.toBeInTheDocument();
  });

  it('should show register_payment button for ISSUED invoice', async () => {
    vi.mocked(billingService.searchInvoices).mockResolvedValueOnce(page([result(ISSUED_INVOICE)]) as never);
    render(<Billing />);
    await waitFor(() => expect(screen.getByText('register_payment')).toBeInTheDocument());
  });

  it('should open PaymentModal when register_payment is clicked', async () => {
    vi.mocked(billingService.searchInvoices).mockResolvedValueOnce(page([result(ISSUED_INVOICE)]) as never);
    render(<Billing />);
    await waitFor(() => screen.getByText('register_payment'));
    fireEvent.click(screen.getByText('register_payment'));
    expect(await screen.findByText('register_payment_title')).toBeInTheDocument();
  });

  it('should close PaymentModal when cancel is clicked', async () => {
    vi.mocked(billingService.searchInvoices).mockResolvedValueOnce(page([result(ISSUED_INVOICE)]) as never);
    render(<Billing />);
    await waitFor(() => screen.getByText('register_payment'));
    fireEvent.click(screen.getByText('register_payment'));
    await screen.findByText('register_payment_title');
    fireEvent.click(screen.getByText('cancel'));
    await waitFor(() =>
      expect(screen.queryByText('register_payment_title')).not.toBeInTheDocument(),
    );
  });

  it('should open InvoiceDetailModal when view is clicked', async () => {
    vi.mocked(billingService.searchInvoices).mockResolvedValueOnce(page([result(PAID_INVOICE)]) as never);
    render(<Billing />);
    await waitFor(() => screen.getByText('view'));
    fireEvent.click(screen.getByText('view'));
    expect(screen.getByText('invoice_detail_title')).toBeInTheDocument();
  });

  it('should close InvoiceDetailModal on close button', async () => {
    vi.mocked(billingService.searchInvoices).mockResolvedValueOnce(page([result(PAID_INVOICE)]) as never);
    render(<Billing />);
    await waitFor(() => screen.getByText('view'));
    fireEvent.click(screen.getByText('view'));
    const closeBtn = screen.getByLabelText('close');
    fireEvent.click(closeBtn);
    expect(screen.queryByText('invoice_detail_title')).not.toBeInTheDocument();
  });

  it('should call processPayment and update invoice status on submit', async () => {
    const user = userEvent.setup();
    vi.mocked(billingService.searchInvoices).mockResolvedValueOnce(page([result(ISSUED_INVOICE)]) as never);
    vi.mocked(billingService.processPayment).mockResolvedValueOnce({
      id: 'pay-new',
      paymentDate: '2026-04-01T15:00:00',
      amount: 150,
      paymentMethod: 'CASH',
      invoiceId: 'inv-1',
    });

    render(<Billing />);
    await waitFor(() => screen.getByText('register_payment'));
    await user.click(screen.getByText('register_payment'));
    await screen.findByText('register_payment_title');

    const amountInput = screen.getByDisplayValue('150');
    await user.clear(amountInput);
    await user.type(amountInput, '150');

    await user.click(screen.getByText('confirm_payment'));

    // Real userEvent keystroke timing through clear+type+click can eat into the 1000ms
    // default under full-suite CPU load; widen the margin for this assertion.
    await waitFor(() =>
      expect(billingService.processPayment).toHaveBeenCalledWith('inv-1', {
        amount: 150,
        paymentMethod: 'CASH',
        transactionReference: undefined,
      }),
      { timeout: 5000 },
    );
  });

  it('should show amount validation error for zero amount', async () => {
    const user = userEvent.setup();
    vi.mocked(billingService.searchInvoices).mockResolvedValueOnce(page([result(ISSUED_INVOICE)]) as never);
    render(<Billing />);
    await waitFor(() => screen.getByText('register_payment'));
    await user.click(screen.getByText('register_payment'));
    await screen.findByText('register_payment_title');

    const amountInput = screen.getByDisplayValue('150');
    await user.clear(amountInput);
    await user.type(amountInput, '0');
    await user.click(screen.getByText('confirm_payment'));

    await waitFor(() =>
      expect(screen.getByText('error_invalid_amount')).toBeInTheDocument(),
    );
  });

  it('should show detail modal with payment history for paid invoice', async () => {
    vi.mocked(billingService.searchInvoices).mockResolvedValueOnce(page([result(PAID_INVOICE)]) as never);
    render(<Billing />);
    await waitFor(() => screen.getByText('view'));
    fireEvent.click(screen.getByText('view'));
    expect(screen.getByText('payments_history')).toBeInTheDocument();
    expect(screen.getByText('payment_method_cash')).toBeInTheDocument();
  });

  it('should show F&B charges in detail modal', async () => {
    vi.mocked(billingService.searchInvoices).mockResolvedValueOnce(page([result(ISSUED_INVOICE)]) as never);
    render(<Billing />);
    await waitFor(() => screen.getByText('view'));
    fireEvent.click(screen.getByText('view'));
    expect(screen.getByText('charges')).toBeInTheDocument();
    expect(screen.getByText('Dinner')).toBeInTheDocument();
  });

  it('should re-query the server with the ISSUED status when the ISSUED chip is clicked', async () => {
    vi.mocked(billingService.searchInvoices)
        .mockResolvedValueOnce(page([result(ISSUED_INVOICE), result(PAID_INVOICE)]) as never)
        .mockResolvedValueOnce(page([result(ISSUED_INVOICE)]) as never);
    render(<Billing />);
    await waitFor(() => expect(screen.getByText('INV-001')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'invoice_status_ISSUED' }));

    await waitFor(() => {
      expect(billingService.searchInvoices).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: 'ISSUED' }),
      );
      expect(screen.queryByText('INV-002')).not.toBeInTheDocument();
    });
  });

  it('should re-query the server with the PAID status when the PAID chip is clicked', async () => {
    vi.mocked(billingService.searchInvoices)
        .mockResolvedValueOnce(page([result(ISSUED_INVOICE), result(PAID_INVOICE)]) as never)
        .mockResolvedValueOnce(page([result(PAID_INVOICE)]) as never);
    render(<Billing />);
    await waitFor(() => expect(screen.getByText('INV-002')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'invoice_status_PAID' }));

    await waitFor(() => {
      expect(billingService.searchInvoices).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: 'PAID' }),
      );
      expect(screen.queryByText('INV-001')).not.toBeInTheDocument();
    });
  });

  it('should re-query with no status filter when the ALL chip is clicked after filtering', async () => {
    vi.mocked(billingService.searchInvoices)
        .mockResolvedValueOnce(page([result(ISSUED_INVOICE), result(PAID_INVOICE)]) as never)
        .mockResolvedValueOnce(page([result(PAID_INVOICE)]) as never)
        .mockResolvedValueOnce(page([result(ISSUED_INVOICE), result(PAID_INVOICE)]) as never);
    render(<Billing />);
    await waitFor(() => expect(screen.getByText('INV-001')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'invoice_status_PAID' }));
    await waitFor(() => expect(screen.queryByText('INV-001')).not.toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'filter_all' }));
    await waitFor(() => {
      expect(billingService.searchInvoices).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: undefined }),
      );
      expect(screen.getByText('INV-001')).toBeInTheDocument();
      expect(screen.getByText('INV-002')).toBeInTheDocument();
    });
  });

  it('should have no accessibility violations on empty state', async () => {
    vi.mocked(billingService.searchInvoices).mockResolvedValueOnce(page([]) as never);
    const { container } = render(<Billing />);
    await waitFor(() => expect(screen.getByText('no_invoices')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });

  it('should have no accessibility violations with invoices', async () => {
    vi.mocked(billingService.searchInvoices)
        .mockResolvedValueOnce(page([result(ISSUED_INVOICE), result(PAID_INVOICE)]) as never);
    const { container } = render(<Billing />);
    await waitFor(() => expect(screen.getByText('INV-001')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
