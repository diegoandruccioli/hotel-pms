import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { InvoiceDetailModal } from './InvoiceDetailModal';
import type { InvoiceResponse } from '../../types/billing.types';
import { billingService } from '../../services/billingService';

vi.mock('../../services/billingService', () => ({
  billingService: {
    downloadPdf: vi.fn(),
    downloadFatturaPAXml: vi.fn(),
    updateDocumentType: vi.fn(),
  },
}));

const mockAddToast = vi.fn();
vi.mock('../../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: typeof mockAddToast }) => unknown)({ addToast: mockAddToast }),
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
  totalAmount: 250, status: 'ISSUED', documentType: 'FATTURA', sdiStatus: 'NOT_SENT',
  reservationId: 'res1', guestId: 'g1', stayId: 's1', payments: [], charges: [],
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
const INVOICE_CANCELLED: InvoiceResponse = { ...BASE_INVOICE, status: 'CANCELLED' };
const INVOICE_RICEVUTA: InvoiceResponse = { ...BASE_INVOICE, documentType: 'RICEVUTA' };

describe('InvoiceDetailModal', () => {
  const onClose = vi.fn();

  beforeEach(() => vi.clearAllMocks());

  it('renders invoice number', () => {
    render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    expect(screen.getByText('INV-001')).toBeInTheDocument();
  });

  it('renders invoice status chip', () => {
    render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    expect(screen.getByText('invoice_status_ISSUED')).toBeInTheDocument();
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
    expect(screen.getByText('invoice_status_PAID')).toBeInTheDocument();
  });

  it('renders download PDF button', () => {
    render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    expect(screen.getByRole('button', { name: /download_pdf/i })).toBeInTheDocument();
  });

  it('calls billingService.downloadPdf on button click', () => {
    render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    fireEvent.click(screen.getByRole('button', { name: /download_pdf/i }));
    expect(billingService.downloadPdf).toHaveBeenCalledWith('inv1');
  });

  it('shows document type toggle for non-cancelled invoices', () => {
    render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    expect(screen.getByText('document_type_fattura')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /switch_to_ricevuta/i })).toBeInTheDocument();
  });

  it('hides document type toggle for cancelled invoices', () => {
    render(<InvoiceDetailModal invoice={INVOICE_CANCELLED} onClose={onClose} />);
    expect(screen.queryByRole('button', { name: /switch_to/i })).not.toBeInTheDocument();
  });

  it('calls updateDocumentType and onUpdated when toggle is clicked', async () => {
    const updated: InvoiceResponse = { ...BASE_INVOICE, documentType: 'RICEVUTA' };
    vi.mocked(billingService.updateDocumentType).mockResolvedValueOnce(updated);
    const onUpdated = vi.fn();

    render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} onUpdated={onUpdated} />);
    fireEvent.click(screen.getByRole('button', { name: /switch_to_ricevuta/i }));

    await waitFor(() => expect(billingService.updateDocumentType).toHaveBeenCalledWith('inv1', 'RICEVUTA'));
    expect(onUpdated).toHaveBeenCalledWith(updated);
    expect(mockAddToast).toHaveBeenCalledWith('document_type_updated', 'success');
  });

  it('shows error toast when updateDocumentType fails', async () => {
    vi.mocked(billingService.updateDocumentType).mockRejectedValueOnce({
      response: { data: { detail: 'CANNOT_UPDATE_CANCELLED_INVOICE' } },
    });

    render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    fireEvent.click(screen.getByRole('button', { name: /switch_to_ricevuta/i }));

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('CANNOT_UPDATE_CANCELLED_INVOICE', 'error'));
  });

  it('shows SDI status chip for FATTURA non-cancelled invoice', () => {
    render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    expect(screen.getByText('sdi_status_label')).toBeInTheDocument();
    expect(screen.getByText('sdi_status_not_sent')).toBeInTheDocument();
  });

  it('shows download FatturaPA button for FATTURA non-cancelled invoice', () => {
    render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    expect(screen.getByRole('button', { name: /download_fattura_pa/i })).toBeInTheDocument();
  });

  it('calls downloadFatturaPAXml when button clicked', () => {
    render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    fireEvent.click(screen.getByRole('button', { name: /download_fattura_pa/i }));
    expect(billingService.downloadFatturaPAXml).toHaveBeenCalledWith('inv1');
  });

  it('hides SDI section for RICEVUTA invoices', () => {
    render(<InvoiceDetailModal invoice={INVOICE_RICEVUTA} onClose={onClose} />);
    expect(screen.queryByText('sdi_status_label')).not.toBeInTheDocument();
  });

  it('hides SDI section for CANCELLED invoices', () => {
    render(<InvoiceDetailModal invoice={INVOICE_CANCELLED} onClose={onClose} />);
    expect(screen.queryByText('sdi_status_label')).not.toBeInTheDocument();
  });

  it('passes axe accessibility check', async () => {
    const { container } = render(<InvoiceDetailModal invoice={BASE_INVOICE} onClose={onClose} />);
    expect(await axe(container)).toHaveNoViolations();
  }, 30000);
});
