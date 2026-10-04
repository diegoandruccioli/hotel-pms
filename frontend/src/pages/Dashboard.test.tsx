import { screen, waitFor, fireEvent, within } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { renderWithQuery } from '../test-utils';
import { Dashboard } from './Dashboard';
import { useAuthStore } from '../store';
import { stayService } from '../services';
import { dashboardService } from '../services';
import { billingReportService } from '../services';
import { reservationService } from '../services';
import { kpiReportService } from '../services';
import type { DaySheetResponse, DaySheetTrendResponse } from '../types';
import type { OwnerFinancialSummaryDto } from '../types';

vi.mock('../services/stayService', () => ({
  stayService: {
    getAlloggiatiFailureSummary: vi.fn(),
    getCityTaxUnassessedSummary: vi.fn(),
    searchStays: vi.fn(),
    checkOut: vi.fn(),
  },
}));

vi.mock('../services/dashboardService', () => ({
  dashboardService: { getDaySheet: vi.fn(), getDaySheetTrend: vi.fn() },
}));

vi.mock('../services/billingReportService', () => ({
  billingReportService: { getOwnerFinancialSummary: vi.fn() },
}));

vi.mock('../services/reservationService', () => ({
  reservationService: { searchReservations: vi.fn() },
}));

vi.mock('../services/kpiReportService', () => ({
  kpiReportService: { getKpiReport: vi.fn() },
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: { name?: string; value?: string }) => {
      if (key === 'welcome_back' && options?.name) return `welcome_back ${options.name}`;
      if (key === 'dashboard_delta_vs_yesterday') return `${key} ${options?.value}`;
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

const EMPTY_PAGE = {
  content: [], totalElements: 0, totalPages: 0, number: 0, size: 8,
  numberOfElements: 0, first: true, last: true, empty: true,
};

const isoDaysAgo = (days: number) => {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

const trendOf = (...points: [number, number][]): DaySheetTrendResponse => ({
  from: isoDaysAgo(7),
  to: isoDaysAgo(1),
  points: points.map(([daysAgo, guestsInHouse]) => ({
    date: isoDaysAgo(daysAgo), arrivals: 4, departures: 3, guestsInHouse, availableRooms: 10,
  })),
});

const LocationProbe = () => {
  const { pathname, state } = useLocation();
  return <div data-testid="location" data-state={JSON.stringify(state)}>{pathname}</div>;
};

const openDeparturesTab = async () => fireEvent.click(await screen.findByRole('radio', { name: 'dashboard_tab_departures' }));

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
    vi.mocked(dashboardService.getDaySheet).mockResolvedValue({ ...MOCK_DAY_SHEET, date: isoDaysAgo(0) });
    vi.mocked(dashboardService.getDaySheetTrend).mockReset();
    vi.mocked(dashboardService.getDaySheetTrend).mockResolvedValue(null);
    vi.mocked(billingReportService.getOwnerFinancialSummary).mockReset();
    vi.mocked(billingReportService.getOwnerFinancialSummary).mockResolvedValue(MOCK_SUMMARY);
    vi.mocked(reservationService.searchReservations).mockReset();
    vi.mocked(reservationService.searchReservations).mockResolvedValue(EMPTY_PAGE);
    vi.mocked(stayService.searchStays).mockReset();
    vi.mocked(stayService.searchStays).mockResolvedValue(EMPTY_PAGE);
    vi.mocked(stayService.checkOut).mockReset();
    vi.mocked(kpiReportService.getKpiReport).mockReset();
    vi.mocked(kpiReportService.getKpiReport).mockResolvedValue({
      periods: [], totals: { periodStart: '', totalRoomRevenue: 0, occupiedRoomNights: 0,
        availableRoomNights: 0, adr: 120, revpar: 90, occupancyRate: 0.75 },
    });
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

  it('shows the arrivals/departures work list with an actionable arrival row', async () => {
    vi.mocked(reservationService.searchReservations).mockResolvedValue({
      ...EMPTY_PAGE,
      content: [{
        id: 'res-1', guestId: 'g1', guestFullName: 'Mario Rossi', checkInDate: '2026-08-20',
        checkOutDate: '2026-08-22', status: 'CONFIRMED', expectedGuests: 2, lineItems: [],
        active: true, createdAt: '', updatedAt: '', confirmationEmailFailed: false,
      }],
    });
    renderDashboard();
    await waitFor(() => expect(screen.getByText('Mario Rossi')).toBeInTheDocument());
    expect(screen.getByTestId('dashboard-check-in-res-1')).toBeInTheDocument();
    await openDeparturesTab();
    expect(screen.getByText('dashboard_no_departures_today')).toBeInTheDocument();
  });

  it('hides the check-in button on an arrival row that is not yet CONFIRMED', async () => {
    vi.mocked(reservationService.searchReservations).mockResolvedValue({
      ...EMPTY_PAGE,
      content: [{
        id: 'res-2', guestId: 'g2', guestFullName: 'Anna Bianchi', checkInDate: '2026-08-20',
        checkOutDate: '2026-08-22', status: 'PENDING', expectedGuests: 1, lineItems: [],
        active: true, createdAt: '', updatedAt: '', confirmationEmailFailed: false,
      }],
    });
    renderDashboard();
    await waitFor(() => expect(screen.getByText('Anna Bianchi')).toBeInTheDocument());
    expect(screen.queryByTestId('dashboard-check-in-res-2')).not.toBeInTheDocument();
  });

  it('shows a populated departure row and checks a guest out on click', async () => {
    vi.mocked(stayService.searchStays).mockResolvedValue({
      ...EMPTY_PAGE,
      content: [{ id: 'stay-1', roomNumber: '101', expectedCheckOutDate: '2026-08-20', status: 'CHECKED_IN' }],
    } as never);
    vi.mocked(stayService.checkOut).mockResolvedValue({} as never);
    renderDashboard();
    await openDeparturesTab();

    await waitFor(() => expect(screen.getByTestId('dashboard-check-out-stay-1')).toBeInTheDocument());
    expect(screen.getByText('101')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('dashboard-check-out-stay-1'));
    await waitFor(() => expect(stayService.checkOut).toHaveBeenCalledWith('stay-1'));
  });

  it('handles a failed check-out from the departures row without crashing', async () => {
    vi.mocked(stayService.searchStays).mockResolvedValue({
      ...EMPTY_PAGE,
      content: [{ id: 'stay-2', roomNumber: '202', expectedCheckOutDate: '2026-08-20', status: 'CHECKED_IN' }],
    } as never);
    vi.mocked(stayService.checkOut).mockRejectedValue(new Error('boom'));
    renderDashboard();
    await openDeparturesTab();

    await waitFor(() => expect(screen.getByTestId('dashboard-check-out-stay-2')).toBeInTheDocument());
    fireEvent.click(screen.getByTestId('dashboard-check-out-stay-2'));
    await waitFor(() => expect(stayService.checkOut).toHaveBeenCalledWith('stay-2'));
  });

  it('shows the owner summary section (occupancy/ADR/RevPAR) for ADMIN', async () => {
    renderDashboard();
    const section = await screen.findByRole('region', { name: 'dashboard_owner_summary_title' });
    expect(within(section).getByText('75%')).toBeInTheDocument();
    expect(within(section).getByText('€120.00')).toBeInTheDocument();
    expect(within(section).getByText('€90.00')).toBeInTheDocument();
    // The cards are plain tiles; the one link is the section's own, named by context.
    expect(within(section).getAllByRole('link')).toHaveLength(1);
    expect(within(section).getByRole('link', { name: 'dashboard_view_all_owner_report' }))
      .toHaveAttribute('href', '/owner-dashboard');
  });

  it('hides the owner summary section for RECEPTIONIST', async () => {
    useAuthStore.setState({
      user: { sub: 'user2', username: 'reception', role: 'RECEPTIONIST' },
      isAuthenticated: true,
      isLoading: false,
    });
    renderDashboard();
    await waitFor(() => expect(screen.getByTestId('stats-grid')).toBeInTheDocument());
    expect(screen.queryByText('dashboard_owner_summary_title')).not.toBeInTheDocument();
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

  it('lists every room status with its count and canonical tone dot', async () => {
    renderDashboard();
    const legend = await screen.findByTestId('room-status-summary');
    const rowOf = (label: string) => within(legend).getByText(label).closest('li');
    expect(rowOf('room_status_clean')).toHaveTextContent('10');
    expect(rowOf('room_status_dirty')).toHaveTextContent('2');
    expect(rowOf('room_status_maintenance')).toHaveTextContent('1');
    expect(rowOf('room_status_occupied')).toHaveTextContent('12');
    expect(rowOf('room_status_clean')?.querySelector('span')?.className).toContain('bg-tertiary');
    expect(rowOf('room_status_dirty')?.querySelector('span')?.className).toContain('bg-secondary');
    expect(rowOf('room_status_maintenance')?.querySelector('span')?.className).toContain('bg-error');
    expect(rowOf('room_status_occupied')?.querySelector('span')?.className).toContain('bg-primary');
  });

  it('offers the to-do list with links built from the day-sheet', async () => {
    renderDashboard();
    expect(await screen.findByText('dashboard_tasks_title')).toBeInTheDocument();
    expect(screen.getByText('dashboard_task_dirty_rooms').closest('a')).toHaveAttribute('href', '/housekeeping');
    expect(screen.getByText('dashboard_task_night_audit').closest('a')).toHaveAttribute('href', '/night-audit');
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

  it('fetches and shows the Alloggiati failure banner for RECEPTIONIST too (GAP-26)', async () => {
    vi.mocked(stayService.getAlloggiatiFailureSummary).mockResolvedValue({
      failedCount: 2, mostRecentFailureAt: '2026-06-19T10:00:00', mostRecentFailureReason: 'PS portal down',
    });
    useAuthStore.setState({
      user: { sub: 'user2', username: 'reception', role: 'RECEPTIONIST' },
      isAuthenticated: true,
      isLoading: false,
    });
    renderDashboard();
    await waitFor(() => {
      expect(screen.getByText('alloggiati_failure_banner_title')).toBeInTheDocument();
    });
    expect(stayService.getAlloggiatiFailureSummary).toHaveBeenCalled();
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

  it('fetches and shows the city-tax unassessed banner for RECEPTIONIST too (GAP-26)', async () => {
    vi.mocked(stayService.getCityTaxUnassessedSummary).mockResolvedValue({
      unassessedCount: 3, mostRecentUnassessedAt: '2026-06-19T10:00:00', mostRecentReason: 'NO_RATE_FOR_DATE',
    });
    useAuthStore.setState({
      user: { sub: 'user2', username: 'reception', role: 'RECEPTIONIST' },
      isAuthenticated: true,
      isLoading: false,
    });
    renderDashboard();
    await waitFor(() => {
      expect(screen.getByText('city_tax_unassessed_banner_title')).toBeInTheDocument();
    });
    expect(stayService.getCityTaxUnassessedSummary).toHaveBeenCalled();
  });

  it('renders error state with retry button', async () => {
    vi.mocked(dashboardService.getDaySheet).mockRejectedValueOnce(new Error('boom'));
    renderDashboard();
    await waitFor(() => expect(screen.getByText('error_loading_dashboard')).toBeInTheDocument());
    expect(screen.getByText('try_again')).toBeInTheDocument();
  });

  it('shows each KPI as one link to its list, without separate "view all" links', async () => {
    renderDashboard();
    const grid = await screen.findByTestId('stats-grid');
    const links = within(grid).getAllByRole('link');
    expect(links.map((l) => l.getAttribute('href'))).toEqual(['/stays', '/reservations', '/stays', '/rooms', '/billing']);
    expect(within(grid).queryByText('view_all')).not.toBeInTheDocument();
  });

  it('shows a signed delta and a sparkline when yesterday has a snapshot', async () => {
    vi.mocked(dashboardService.getDaySheetTrend).mockResolvedValue(trendOf([2, 190], [1, 195]));
    renderDashboard();
    const grid = await screen.findByTestId('stats-grid');
    await waitFor(() => expect(within(grid).getByText('dashboard_delta_vs_yesterday +5')).toBeInTheDocument());
    expect(grid.querySelectorAll('svg polyline')).toHaveLength(4);
    expect(within(grid).getAllByText('dashboard_trend_label')).toHaveLength(4);
  });

  it('shows a negative delta with a minus sign', async () => {
    vi.mocked(dashboardService.getDaySheetTrend).mockResolvedValue(trendOf([1, 210]));
    renderDashboard();
    await waitFor(() => expect(screen.getByText('dashboard_delta_vs_yesterday −10')).toBeInTheDocument());
  });

  it('keeps the sparkline but drops the delta when yesterday has no snapshot', async () => {
    vi.mocked(dashboardService.getDaySheetTrend).mockResolvedValue(trendOf([3, 190], [2, 195]));
    renderDashboard();
    const grid = await screen.findByTestId('stats-grid');
    await waitFor(() => expect(grid.querySelectorAll('svg polyline').length).toBeGreaterThan(0));
    expect(within(grid).queryByText('dashboard_delta_vs_yesterday', { exact: false })).not.toBeInTheDocument();
  });

  it('renders KPIs without trend when the endpoint is missing or empty', async () => {
    vi.mocked(dashboardService.getDaySheetTrend).mockResolvedValue({ from: '', to: '', points: [] });
    renderDashboard();
    const grid = await screen.findByTestId('stats-grid');
    expect(grid.querySelectorAll('svg polyline')).toHaveLength(0);
    expect(within(grid).queryByText('dashboard_delta_vs_yesterday', { exact: false })).not.toBeInTheDocument();
  });

  it('renders KPIs when the trend request fails', async () => {
    vi.mocked(dashboardService.getDaySheetTrend).mockRejectedValue(new Error('boom'));
    renderDashboard();
    expect(await screen.findByTestId('stats-grid')).toBeInTheDocument();
    expect(screen.queryByText('error_loading_dashboard')).not.toBeInTheDocument();
  });

  it('passes the list filter as router state when a KPI card is clicked', async () => {
    renderWithQuery(
      <MemoryRouter>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="*" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>,
    );
    const grid = await screen.findByTestId('stats-grid');
    fireEvent.click(within(grid).getAllByRole('link')[1]);
    const probe = await screen.findByTestId('location');
    expect(probe).toHaveTextContent('/reservations');
    expect(JSON.parse(probe.getAttribute('data-state') ?? '{}')).toEqual({ upcomingOnly: true, sortField: 'checkInDate', sortDir: 'asc' });
  });

  it('leaves out a zero delta', async () => {
    vi.mocked(dashboardService.getDaySheetTrend).mockResolvedValue(trendOf([1, 200]));
    renderDashboard();
    const grid = await screen.findByTestId('stats-grid');
    await waitFor(() => expect(within(grid).getAllByText('dashboard_trend_label').length).toBeGreaterThan(0));
    expect(within(grid).queryByText('dashboard_delta_vs_yesterday 0')).not.toBeInTheDocument();
  });

  it.each([
    ['new_reservation', '/reservations/new'],
    ['dashboard_action_walk_in', '/stays/walk-in'],
  ])('header action %s navigates to %s', async (label, path) => {
    renderWithQuery(
      <MemoryRouter>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="*" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: new RegExp(label) }));
    expect(await screen.findByTestId('location')).toHaveTextContent(path);
  });

  it('has no accessibility violations', async () => {
    const { container } = renderDashboard();
    await waitFor(() => expect(screen.getByTestId('stats-grid')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
