import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { OwnerDashboard } from './OwnerDashboard';
import { billingReportService } from '../services/billingReportService';
import type { OwnerFinancialReportDto } from '../types/ownerReport.types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/billingReportService', () => ({
  billingReportService: { getOwnerFinancialReport: vi.fn(), exportToCsv: vi.fn() },
}));

vi.mock('../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

const mockUseAuthStore = vi.fn();
vi.mock('../store/authStore', () => ({
  useAuthStore: () => mockUseAuthStore(),
}));

describe('OwnerDashboard', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should show access restricted for non-owner', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'RECEPTIONIST' } });
    render(<OwnerDashboard />);
    expect(screen.getByText('access_restricted')).toBeInTheDocument();
  });

  it('should show access restricted for unauthenticated user', () => {
    mockUseAuthStore.mockReturnValue({ user: null });
    render(<OwnerDashboard />);
    expect(screen.getByText('access_restricted')).toBeInTheDocument();
  });

  it('should render dashboard for OWNER role', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    render(<OwnerDashboard />);
    expect(screen.getByText('owner_dashboard')).toBeInTheDocument();
  });

  it('should render dashboard for ADMIN role', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'ADMIN' } });
    render(<OwnerDashboard />);
    expect(screen.getByText('owner_dashboard')).toBeInTheDocument();
  });

  it('should show date filter fields for authorized users', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    render(<OwnerDashboard />);
    expect(screen.getByLabelText('start_date')).toBeInTheDocument();
    expect(screen.getByLabelText('end_date')).toBeInTheDocument();
  });

  it('should show generate report button', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    render(<OwnerDashboard />);
    expect(screen.getByText('generate_report')).toBeInTheDocument();
  });

  it('should have no accessibility violations', async () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    const { container } = render(<OwnerDashboard />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });

  const REPORT: OwnerFinancialReportDto = {
    startDate: '2026-06-01',
    endDate: '2026-06-30',
    totalRevenue: 1234.5,
    totalInvoices: 4,
    paidInvoices: 3,
    invoices: [
      { id: 'i1', invoiceNumber: 'INV-1', issueDate: '2026-06-01', totalAmount: 100, status: 'PAID', documentType: 'FATTURA' as const, reservationId: 'r1', guestId: 'g1', payments: [] },
      { id: 'i2', invoiceNumber: 'INV-2', issueDate: '2026-06-02', totalAmount: 200, status: 'ISSUED', documentType: 'FATTURA' as const, reservationId: 'r2', guestId: 'g2', payments: [] },
      { id: 'i3', invoiceNumber: 'INV-3', issueDate: '2026-06-03', totalAmount: 50, status: 'CANCELLED', documentType: 'FATTURA' as const, reservationId: 'r3', guestId: 'g3', payments: [] },
      { id: 'i4', invoiceNumber: 'INV-4', issueDate: undefined as unknown as string, totalAmount: 884.5, status: 'DRAFT' as never, documentType: 'FATTURA' as const, reservationId: 'r4', guestId: 'g4', payments: [] },
    ],
  };

  it('loads and renders a report with revenue, invoices, collection rate, and CSV export', async () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    vi.mocked(billingReportService.getOwnerFinancialReport).mockResolvedValueOnce(REPORT);
    render(<OwnerDashboard />);

    fireEvent.click(screen.getByText('generate_report'));

    await waitFor(() => expect(screen.getByText('INV-1')).toBeInTheDocument());
    expect(billingReportService.getOwnerFinancialReport).toHaveBeenCalledWith(
      expect.any(String), expect.any(String),
    );
    expect(screen.getByText('PAID')).toBeInTheDocument();
    expect(screen.getByText('ISSUED')).toBeInTheDocument();
    expect(screen.getByText('CANCELLED')).toBeInTheDocument();
    expect(screen.getByText('DRAFT')).toBeInTheDocument();
    expect(screen.getByText(/75% collection_rate/)).toBeInTheDocument();
    expect(screen.getByText('export_csv')).toBeInTheDocument();

    fireEvent.click(screen.getByText('export_csv'));
    expect(billingReportService.exportToCsv).toHaveBeenCalledWith(REPORT);
  });

  it('shows the no_invoices_period message when the report has zero invoices', async () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    vi.mocked(billingReportService.getOwnerFinancialReport).mockResolvedValueOnce({
      ...REPORT, totalInvoices: 0, paidInvoices: 0, invoices: [],
    });
    render(<OwnerDashboard />);

    fireEvent.click(screen.getByText('generate_report'));

    await waitFor(() => expect(screen.getByText('no_invoices_period')).toBeInTheDocument());
    expect(screen.getByText('no_invoices')).toBeInTheDocument();
  });

  it('shows an error toast and banner when the report fails to load', async () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    vi.mocked(billingReportService.getOwnerFinancialReport).mockRejectedValueOnce(new Error('boom'));
    render(<OwnerDashboard />);

    fireEvent.click(screen.getByText('generate_report'));

    await waitFor(() => expect(screen.getByText('boom')).toBeInTheDocument());
  });

  it('updates start and end date inputs', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    render(<OwnerDashboard />);

    fireEvent.change(screen.getByLabelText('start_date'), { target: { value: '2026-01-01' } });
    fireEvent.change(screen.getByLabelText('end_date'), { target: { value: '2026-01-31' } });

    expect((screen.getByLabelText('start_date') as HTMLInputElement).value).toBe('2026-01-01');
    expect((screen.getByLabelText('end_date') as HTMLInputElement).value).toBe('2026-01-31');
  });
});
