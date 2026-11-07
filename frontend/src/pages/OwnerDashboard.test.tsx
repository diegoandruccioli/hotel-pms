import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor, within } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { OwnerDashboard } from './OwnerDashboard';
import { billingReportService } from '../services';
import { kpiReportService } from '../services';
import { MemoryRouter } from 'react-router-dom';
import type { ReactElement } from 'react';
import { renderWithQuery as renderWithQueryOnly } from '../test-utils';
import type { OwnerFinancialReportDto, OwnerFinancialSummaryDto } from '../types';
import { mockAxiosErrorWithDetail } from '../test-utils';

// PageHeader derives its breadcrumbs from the router location.
const renderWithQuery = (ui: ReactElement) => renderWithQueryOnly(<MemoryRouter>{ui}</MemoryRouter>);

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: { value?: string }) => (options?.value ? `${key} ${options.value}` : key),
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/billingReportService', () => ({
  billingReportService: {
    getOwnerFinancialReport: vi.fn(),
    getOwnerFinancialSummary: vi.fn(),
    exportToCsv: vi.fn(),
  },
}));

vi.mock('../services/kpiReportService', () => ({
  kpiReportService: { getKpiReport: vi.fn() },
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
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(kpiReportService.getKpiReport).mockResolvedValue({
      periods: [], totals: { periodStart: '', totalRoomRevenue: 0, occupiedRoomNights: 0,
        availableRoomNights: 0, adr: 0, revpar: 0, occupancyRate: 0 },
    });
    // Previous-period comparison call the report load now makes alongside
    // getOwnerFinancialReport — default to an empty baseline so the KPI cards
    // have nothing to compare against (previous === 0) and show no delta
    // unless a test explicitly overrides this to assert on it.
    vi.mocked(billingReportService.getOwnerFinancialSummary).mockResolvedValue({
      startDate: '', endDate: '', totalRevenue: 0, totalInvoices: 0, paidInvoices: 0, pendingRevenue: 0,
    });
  });

  it('should show access restricted for non-owner', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'RECEPTIONIST' } });
    renderWithQuery(<OwnerDashboard />);
    expect(screen.getByText('access_restricted')).toBeInTheDocument();
  });

  it('should show access restricted for unauthenticated user', () => {
    mockUseAuthStore.mockReturnValue({ user: null });
    renderWithQuery(<OwnerDashboard />);
    expect(screen.getByText('access_restricted')).toBeInTheDocument();
  });

  it('should render dashboard for OWNER role', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    renderWithQuery(<OwnerDashboard />);
    expect(screen.getByText('owner_dashboard')).toBeInTheDocument();
  });

  it('should render dashboard for ADMIN role', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'ADMIN' } });
    renderWithQuery(<OwnerDashboard />);
    expect(screen.getByText('owner_dashboard')).toBeInTheDocument();
  });

  it('should show date filter fields for authorized users', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    renderWithQuery(<OwnerDashboard />);
    expect(screen.getByLabelText('start_date')).toBeInTheDocument();
    expect(screen.getByLabelText('end_date')).toBeInTheDocument();
  });

  it('should show generate report button', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    renderWithQuery(<OwnerDashboard />);
    expect(screen.getByText('generate_report')).toBeInTheDocument();
  });

  it('should have no accessibility violations', async () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    const { container } = renderWithQuery(<OwnerDashboard />);
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
      { id: 'i1', invoiceNumber: 'INV-1', issueDate: '2026-06-01', totalAmount: 100, status: 'PAID', documentType: 'FATTURA' as const, sdiStatus: 'NOT_SENT' as const, reservationId: 'r1', guestId: 'g1', payments: [] },
      { id: 'i2', invoiceNumber: 'INV-2', issueDate: '2026-06-02', totalAmount: 200, status: 'ISSUED', documentType: 'FATTURA' as const, sdiStatus: 'NOT_SENT' as const, reservationId: 'r2', guestId: 'g2', payments: [] },
      { id: 'i3', invoiceNumber: 'INV-3', issueDate: '2026-06-03', totalAmount: 50, status: 'CANCELLED', documentType: 'FATTURA' as const, sdiStatus: 'NOT_SENT' as const, reservationId: 'r3', guestId: 'g3', payments: [] },
      { id: 'i4', invoiceNumber: 'INV-4', issueDate: undefined as unknown as string, totalAmount: 884.5, status: 'DRAFT' as never, documentType: 'FATTURA' as const, sdiStatus: 'NOT_SENT' as const, reservationId: 'r4', guestId: 'g4', payments: [] },
    ],
  };

  it('loads and renders a report with revenue, invoices, collection rate, and CSV export', async () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    vi.mocked(billingReportService.getOwnerFinancialReport).mockResolvedValueOnce(REPORT);
    renderWithQuery(<OwnerDashboard />);

    fireEvent.click(screen.getByText('generate_report'));

    await waitFor(() => expect(screen.getByText('INV-1')).toBeInTheDocument());
    expect(billingReportService.getOwnerFinancialReport).toHaveBeenCalledWith(
      expect.any(String), expect.any(String),
    );
    expect(screen.getByText('invoice_status_PAID')).toBeInTheDocument();
    expect(screen.getByText('invoice_status_ISSUED')).toBeInTheDocument();
    expect(screen.getByText('invoice_status_CANCELLED')).toBeInTheDocument();
    expect(screen.getByText('invoice_status_DRAFT')).toBeInTheDocument();
    expect(within(screen.getByTestId('owner-kpi-grid')).getByText('75%')).toBeInTheDocument();
    expect(screen.getByText('export_csv')).toBeInTheDocument();

    fireEvent.click(screen.getByText('export_csv'));
    expect(billingReportService.exportToCsv).toHaveBeenCalledWith(
      expect.any(String), expect.any(String),
    );
  });

  const loadReport = async (previous: Partial<OwnerFinancialSummaryDto> | 'fail' = {}) => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    vi.mocked(billingReportService.getOwnerFinancialReport).mockResolvedValueOnce(REPORT);
    if (previous === 'fail') {
      vi.mocked(billingReportService.getOwnerFinancialSummary).mockRejectedValueOnce(new Error('boom'));
    } else {
      vi.mocked(billingReportService.getOwnerFinancialSummary).mockResolvedValueOnce({
        startDate: '2026-05-01', endDate: '2026-05-30', totalRevenue: 0, totalInvoices: 0, paidInvoices: 0,
        pendingRevenue: 0, ...previous,
      });
    }
    renderWithQuery(<OwnerDashboard />);
    fireEvent.click(screen.getByText('generate_report'));
    await waitFor(() => expect(screen.getByText('INV-1')).toBeInTheDocument());
    return screen.getByTestId('owner-kpi-grid');
  };

  it('renders the four KPI cards with the collection rate', async () => {
    const grid = await loadReport();
    expect(within(grid).getByText('total_revenue')).toBeInTheDocument();
    expect(within(grid).getByText('€1,234.50')).toBeInTheDocument();
    expect(within(grid).getByText('total_invoices')).toBeInTheDocument();
    expect(within(grid).getByText('4')).toBeInTheDocument();
    expect(within(grid).getByText('paid_invoices')).toBeInTheDocument();
    expect(within(grid).getByText('3')).toBeInTheDocument();
    expect(within(grid).getByText('collection_rate_title')).toBeInTheDocument();
    expect(within(grid).getByText('75%')).toBeInTheDocument();
  });

  it('words each change against the previous period with its sign', async () => {
    // Revenue 1234.5 vs 1000 = +23%; invoices 4 vs 4 = none; paid 3 vs 2 = +50%; rate 75% vs 50% = +25 pts.
    const grid = await loadReport({ totalRevenue: 1000, totalInvoices: 4, paidInvoices: 2 });
    expect(within(grid).getByText('delta_vs_previous_period +23%')).toBeInTheDocument();
    expect(within(grid).getByText('delta_vs_previous_period +50%')).toBeInTheDocument();
    expect(within(grid).getByText('delta_points_vs_previous_period +25')).toBeInTheDocument();
    expect(within(grid).getAllByText(/^delta_vs_previous_period/)).toHaveLength(2);
  });

  it('shows a drop with a minus sign and the error tone', async () => {
    const grid = await loadReport({ totalRevenue: 2469 });
    const drop = within(grid).getByText('delta_vs_previous_period −50%');
    expect(drop.closest('p')).toHaveClass('text-error');
  });

  it('shows no change when the previous period had no baseline', async () => {
    const grid = await loadReport();
    expect(within(grid).queryByText(/^delta_/)).not.toBeInTheDocument();
  });

  it('shows no change when the comparison could not be loaded', async () => {
    const grid = await loadReport('fail');
    expect(within(grid).queryByText(/^delta_/)).not.toBeInTheDocument();
  });

  it('shows the no_invoices_period message when the report has zero invoices', async () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    vi.mocked(billingReportService.getOwnerFinancialReport).mockResolvedValueOnce({
      ...REPORT, totalInvoices: 0, paidInvoices: 0, invoices: [],
    });
    renderWithQuery(<OwnerDashboard />);

    fireEvent.click(screen.getByText('generate_report'));

    await waitFor(() => expect(screen.getByText('no_invoices_period')).toBeInTheDocument());
    // Nothing issued, nothing to collect: a placeholder, not "0%".
    expect(within(screen.getByTestId('owner-kpi-grid')).getByText('—')).toBeInTheDocument();
  });

  it('has no accessibility violations with a loaded report', async () => {
    const grid = await loadReport({ totalRevenue: 1000, paidInvoices: 2, totalInvoices: 4 });
    expect(await axe(grid.parentElement as HTMLElement)).toHaveNoViolations();
  });

  it('shows an error toast and banner when the report fails to load', async () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    vi.mocked(billingReportService.getOwnerFinancialReport)
      .mockRejectedValueOnce(mockAxiosErrorWithDetail('Report non disponibile per il periodo selezionato'));
    renderWithQuery(<OwnerDashboard />);

    fireEvent.click(screen.getByText('generate_report'));

    await waitFor(() =>
      expect(screen.getByText('Report non disponibile per il periodo selezionato')).toBeInTheDocument());
  });

  it('updates start and end date inputs', () => {
    mockUseAuthStore.mockReturnValue({ user: { role: 'OWNER' } });
    renderWithQuery(<OwnerDashboard />);

    fireEvent.change(screen.getByLabelText('start_date'), { target: { value: '2026-01-01' } });
    fireEvent.change(screen.getByLabelText('end_date'), { target: { value: '2026-01-31' } });

    expect((screen.getByLabelText('start_date') as HTMLInputElement).value).toBe('2026-01-01');
    expect((screen.getByLabelText('end_date') as HTMLInputElement).value).toBe('2026-01-31');
  });
});
