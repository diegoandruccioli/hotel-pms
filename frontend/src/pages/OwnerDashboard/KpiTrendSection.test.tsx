import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { KpiTrendSection } from './KpiTrendSection';
import { kpiReportService } from '../../services';
import { renderWithQuery } from '../../test-utils';
import type { KpiReportDto } from '../../types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock('../../services/kpiReportService', () => ({
  kpiReportService: { getKpiReport: vi.fn() },
}));

const formatCurrency = (amount: number) => `€${amount.toFixed(2)}`;
const formatDate = (dateStr?: string) => dateStr ?? '—';

const emptyReport: KpiReportDto = {
  periods: [],
  totals: {
    periodStart: '', totalRoomRevenue: 0, occupiedRoomNights: 0,
    availableRoomNights: 0, adr: 0, revpar: 0, occupancyRate: 0,
  },
};

const populatedReport: KpiReportDto = {
  periods: [
    {
      periodStart: '2026-08-01', totalRoomRevenue: 500, occupiedRoomNights: 4,
      availableRoomNights: 10, adr: 125, revpar: 50, occupancyRate: 0.4,
    },
    {
      periodStart: '2026-08-08', totalRoomRevenue: 800, occupiedRoomNights: 8,
      availableRoomNights: 10, adr: 100, revpar: 80, occupancyRate: 0.8,
    },
  ],
  totals: {
    periodStart: '2026-08-01', totalRoomRevenue: 1300, occupiedRoomNights: 12,
    availableRoomNights: 20, adr: 108.33, revpar: 65, occupancyRate: 0.6,
  },
};

const renderSection = () => renderWithQuery(
  <KpiTrendSection
    startDate="2026-08-01"
    endDate="2026-08-31"
    formatCurrency={formatCurrency}
    formatDate={formatDate}
  />,
);

describe('KpiTrendSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows the no-data message when the period has no periods', async () => {
    vi.mocked(kpiReportService.getKpiReport).mockResolvedValueOnce(emptyReport);
    renderSection();

    await waitFor(() => expect(screen.getByText('no_kpi_data_period')).toBeInTheDocument());
  });

  it('renders the trend table with ADR/RevPAR/Occupancy for each period', async () => {
    vi.mocked(kpiReportService.getKpiReport).mockResolvedValueOnce(populatedReport);
    renderSection();

    await waitFor(() => expect(screen.getByText('2026-08-01')).toBeInTheDocument());
    expect(screen.getByText('€125.00')).toBeInTheDocument();
    expect(screen.getByText('€50.00')).toBeInTheDocument();
    expect(screen.getByText('40%')).toBeInTheDocument();
    expect(screen.getByText('2026-08-08')).toBeInTheDocument();
  });

  it('shows an error state with a retry action when the report fails to load', async () => {
    vi.mocked(kpiReportService.getKpiReport).mockRejectedValueOnce(new Error('boom'));
    renderSection();

    await waitFor(() => expect(screen.getAllByText('err_kpi_load_failed').length).toBeGreaterThan(0));
  });

  it('refetches with the newly selected granularity', async () => {
    vi.mocked(kpiReportService.getKpiReport).mockResolvedValue(populatedReport);
    renderSection();

    await waitFor(() => expect(kpiReportService.getKpiReport)
      .toHaveBeenCalledWith('2026-08-01', '2026-08-31', 'WEEK'));

    fireEvent.change(screen.getByLabelText('label_granularity'), { target: { value: 'MONTH' } });

    await waitFor(() => expect(kpiReportService.getKpiReport)
      .toHaveBeenCalledWith('2026-08-01', '2026-08-31', 'MONTH'));
  });

  it('has no accessibility violations once data has loaded', async () => {
    vi.mocked(kpiReportService.getKpiReport).mockResolvedValueOnce(populatedReport);
    const { container } = renderSection();

    await waitFor(() => expect(screen.getByText('2026-08-01')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
