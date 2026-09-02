import { screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { renderWithQuery } from '../test-utils';
import { Dashboard } from './Dashboard';
import { useAuthStore } from '../store';
import { stayService } from '../services/stayService';
import { dashboardService } from '../services/dashboardService';
import { billingReportService } from '../services/billingReportService';
import type { DaySheetResponse } from '../types';
import type { OwnerFinancialSummaryDto } from '../types';

vi.mock('../services/stayService', () => ({
  stayService: { getAlloggiatiFailureSummary: vi.fn(), getCityTaxUnassessedSummary: vi.fn() },
}));

vi.mock('../services/dashboardService', () => ({
  dashboardService: { getDaySheet: vi.fn() },
}));

vi.mock('../services/billingReportService', () => ({
  billingReportService: { getOwnerFinancialSummary: vi.fn() },
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: { name?: string }) => {
      if (key === 'welcome_back' && options?.name) return `welcome_back ${options.name}`;
      return key;
    },
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const MOCK_DAY_SHEET: DaySheetResponse = {
  date: '2026-08-20',
  todayArrivals: 5,
  todayDepartures: 3,
  guestsInHouse: 200,
  currentStays: 12,
  availableRooms: 8,
  roomStatusCounts: { CLEAN: 10, DIRTY: 2, MAINTENANCE: 1, OCCUPIED: 12 },
};

const MOCK_SUMMARY: OwnerFinancialSummaryDto = {
  startDate: '2000-01-01',
  endDate: '2099-12-31',
  totalRevenue: 50000,
  totalInvoices: 300,
  paidInvoices: 280,
  pendingRevenue: 10000,
};

const renderDashboard = () =>
  renderWithQuery(<MemoryRouter><Dashboard /></MemoryRouter>);

describe('Dashboard Component', () => {
  beforeEach(() => {
    vi.mocked(stayService.getAlloggiatiFailureSummary).mockReset();
    vi.mocked(stayService.getAlloggiatiFailureSummary).mockResolvedValue({
      failedCount: 0, mostRecentFailureAt: null, mostRecentFailureReason: null,
    });
    vi.mocked(stayService.getCityTaxUnassessedSummary).mockReset();
    vi.mocked(stayService.getCityTaxUnassessedSummary).mockResolvedValue({
      unassessedCount: 0, mostRecentUnassessedAt: null, mostRecentReason: null,
    });
    vi.mocked(dashboardService.getDaySheet).mockReset();
    vi.mocked(dashboardService.getDaySheet).mockResolvedValue(MOCK_DAY_SHEET);
    vi.mocked(billingReportService.getOwnerFinancialSummary).mockReset();
    vi.mocked(billingReportService.getOwnerFinancialSummary).mockResolvedValue(MOCK_SUMMARY);
    useAuthStore.setState({
      user: { sub: 'user1', username: 'admin', role: 'ADMIN' },
      isAuthenticated: true,
      isLoading: false,
    });
  });

  it('renders dashboard heading and stats grid', async () => {
    renderDashboard();
    expect(screen.getByTestId('dashboard-page')).toBeInTheDocument();
    expect(screen.getByTestId('dashboard-heading')).toHaveTextContent('welcome_back admin');
    await waitFor(() => expect(screen.getByTestId('stats-grid')).toBeInTheDocument());
  });

  it('shows today arrivals and departures counts', async () => {
    renderDashboard();
    await waitFor(() => expect(screen.getByText('5')).toBeInTheDocument());
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('8')).toBeInTheDocument();
    expect(screen.getByText('200')).toBeInTheDocument();
  });

  it('shows pending revenue card for ADMIN', async () => {
    renderDashboard();
    await waitFor(() => expect(screen.getByText('stat_pending_revenue')).toBeInTheDocument());
  });

  it('hides pending revenue card for RECEPTIONIST and skips the summary call', async () => {
    useAuthStore.setState({
      user: { sub: 'user2', username: 'reception', role: 'RECEPTIONIST' },
      isAuthenticated: true,
      isLoading: false,
    });
    renderDashboard();
    await waitFor(() => expect(screen.getByTestId('stats-grid')).toBeInTheDocument());
    expect(screen.queryByText('stat_pending_revenue')).not.toBeInTheDocument();
    expect(billingReportService.getOwnerFinancialSummary).not.toHaveBeenCalled();
  });

  it('renders loading state', () => {
    vi.mocked(dashboardService.getDaySheet).mockReturnValue(new Promise(() => {}));
    renderDashboard();
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('renders room status summary with counts per status', async () => {
    renderDashboard();
    await waitFor(() => expect(screen.getByTestId('room-status-summary')).toBeInTheDocument());
    expect(screen.getByText('10')).toBeInTheDocument(); // CLEAN
    expect(screen.getByText('2')).toBeInTheDocument();  // DIRTY
  });

  it('shows Alloggiati failure banner for ADMIN when failures exist', async () => {
    vi.mocked(stayService.getAlloggiatiFailureSummary).mockResolvedValue({
      failedCount: 2, mostRecentFailureAt: '2026-06-19T10:00:00', mostRecentFailureReason: 'PS portal down',
    });
    renderDashboard();
    await waitFor(() => {
      expect(screen.getByText('alloggiati_failure_banner_title')).toBeInTheDocument();
    });
  });

  it('does not fetch or show Alloggiati failure banner for RECEPTIONIST', async () => {
    useAuthStore.setState({
      user: { sub: 'user2', username: 'reception', role: 'RECEPTIONIST' },
      isAuthenticated: true,
      isLoading: false,
    });
    renderDashboard();
    await waitFor(() => expect(screen.getByTestId('stats-grid')).toBeInTheDocument());
    expect(stayService.getAlloggiatiFailureSummary).not.toHaveBeenCalled();
    expect(screen.queryByText('alloggiati_failure_banner_title')).not.toBeInTheDocument();
  });

  it('shows city-tax unassessed banner for ADMIN when gaps exist, linking to Settings', async () => {
    vi.mocked(stayService.getCityTaxUnassessedSummary).mockResolvedValue({
      unassessedCount: 3, mostRecentUnassessedAt: '2026-06-19T10:00:00', mostRecentReason: 'NO_RATE_FOR_DATE',
    });
    renderDashboard();
    await waitFor(() => {
      expect(screen.getByText('city_tax_unassessed_banner_title')).toBeInTheDocument();
    });
    expect(screen.getByText('city_tax_unassessed_banner_action')).toHaveAttribute('href', '/settings/city-tax');
  });

  it('does not fetch or show city-tax unassessed banner for RECEPTIONIST', async () => {
    useAuthStore.setState({
      user: { sub: 'user2', username: 'reception', role: 'RECEPTIONIST' },
      isAuthenticated: true,
      isLoading: false,
    });
    renderDashboard();
    await waitFor(() => expect(screen.getByTestId('stats-grid')).toBeInTheDocument());
    expect(stayService.getCityTaxUnassessedSummary).not.toHaveBeenCalled();
    expect(screen.queryByText('city_tax_unassessed_banner_title')).not.toBeInTheDocument();
  });

  it('renders error state with retry button', async () => {
    vi.mocked(dashboardService.getDaySheet).mockRejectedValueOnce(new Error('boom'));
    renderDashboard();
    await waitFor(() => expect(screen.getByText('error_loading_dashboard')).toBeInTheDocument());
    expect(screen.getByText('try_again')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = renderDashboard();
    await waitFor(() => expect(screen.getByTestId('stats-grid')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
