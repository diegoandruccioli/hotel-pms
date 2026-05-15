import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { InvoiceDetailModal } from './InvoiceDetailModal';
import type { InvoiceResponse } from '../../types/billing.types';
import { billingService } from '../../services/billingService';

vi.mock('../../services/billingService', () => ({
  billingService: {
    downloadPdf: vi.fn().mockResolvedValue(undefined),
  },
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../components/m3/M3Dialog', () => ({
  M3Dialog: ({ children, title }: { children: React.ReactNode; title: string }) => (
    <div role="dialog" aria-label={title}>{children}</div>
  ),
}));

vi.mock('../../components/m3/M3StatusChip', () => ({
  M3StatusChip: ({ label }: { label: string }) => <span>{label}</span>,
}));

const BASE_INVOICE: InvoiceResponse = {
  id: 'inv1', invoiceNumber: 'INV-001', issueDate: '2026-01-01T10:00:00',
  totalAmount: 250, status: 'ISSUED', reservationId: 'res1',
  guestId: 'g1', stayId: 's1', payments: [], charges: [],
};

const INVOICE_WITH_PAYMENT: InvoiceResponse = {
  ...BASE_INVOICE,
  payments: [
    { id: 'p1', paymentDate: '2026-01-02T14:00:00', amount: 100, paymentMethod: 'CASH', transactionReference: 'TXN-1', invoiceId: 'inv1' },
  ],
};

const INVOICE_WITH_CHARGE: InvoiceResponse = {
  ...BASE_INVOICE,
  charges: [
    { id: 'c1', type: 'FB_ORDER' as const, description: 'Espresso', amount: 5 },
  ],
};

const INVOICE_PAID: InvoiceResponse = { ...BASE_INVOICE, charges: [], status: 'PAID' };

describe('InvoiceDetailModal', () => {
  const onClose = vi.fn();

  beforeEach(() => vi.clearAllMocks());

  it('renders invoice number', () => {
    render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    expect(screen.getByText('INV-001')).toBeInTheDocument();
  });

  it('renders invoice status chip', () => {
    render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    expect(screen.getByText('ISSUED')).toBeInTheDocument();
  });

  it('shows no_payments_yet when payments list is empty', () => {
    render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    expect(screen.getByText('no_payments_yet')).toBeInTheDocument();
  });

  it('renders payment rows when payments exist', () => {
    render(<InvoiceDetailModal invoice={INVOICE_WITH_PAYMENT} onClose={onClose} />);
    expect(screen.getByText('payment_method_cash')).toBeInTheDocument();
    expect(screen.getByText('TXN-1', { exact: false })).toBeInTheDocument();
  });

  it('renders charge rows when charges exist', () => {
    render(<InvoiceDetailModal invoice={INVOICE_WITH_CHARGE} onClose={onClose} />);
    expect(screen.getByText('Espresso')).toBeInTheDocument();
  });

  it('renders PAID status correctly', () => {
    render(<InvoiceDetailModal invoice={INVOICE_PAID} onClose={onClose} />);
    expect(screen.getByText('PAID')).toBeInTheDocument();
  });

  it('renders download PDF button', () => {
    render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    expect(screen.getByRole('button', { name: /download_pdf/i })).toBeInTheDocument();
  });

  it('calls billingService.downloadPdf on button click', async () => {
    render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    fireEvent.click(screen.getByRole('button', { name: /download_pdf/i }));
    await waitFor(() => {
      expect(billingService.downloadPdf).toHaveBeenCalledWith('inv1', 'INV-001');
    });
  });

  it('disables button while downloading', async () => {
    let resolve!: () => void;
    vi.mocked(billingService.downloadPdf).mockImplementationOnce(
      () => new Promise<void>((res) => { resolve = res; }),
    );
    render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    fireEvent.click(screen.getByRole('button', { name: /download_pdf/i }));
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /pdf_downloading/i })).toBeDisabled();
    });
    resolve();
  });

  it('passes axe accessibility check', async () => {
    const { container } = render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    expect(await axe(container)).toHaveNoViolations();
  }, 30000);
});
