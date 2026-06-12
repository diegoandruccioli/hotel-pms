import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { Dashboard } from './Dashboard';
import { axe } from 'vitest-axe';
import { useDashboardStore } from '../store/dashboardStore';
import { useAuthStore } from '../store/authStore';

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

const MOCK_STATS_ADMIN = {
  totalGuests: 200,
  todayArrivals: 5,
  todayDepartures: 3,
  currentStays: 12,
  availableRooms: 8,
  pendingRevenue: 10000,
  rooms: [],
};

const renderDashboard = () => render(<MemoryRouter><Dashboard /></MemoryRouter>);

describe('Dashboard Component', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      stats: MOCK_STATS_ADMIN,
      isLoading: false,
      error: null,
      fetchStats: vi.fn(),
    });
    useAuthStore.setState({
      user: { sub: 'user1', username: 'admin', role: 'ADMIN' },
      isAuthenticated: true,
      isLoading: false,
    });
  });

  it('renders dashboard heading and stats grid', () => {
    renderDashboard();
    expect(screen.getByTestId('dashboard-page')).toBeInTheDocument();
    expect(screen.getByTestId('dashboard-heading')).toHaveTextContent('welcome_back admin');
    expect(screen.getByTestId('stats-grid')).toBeInTheDocument();
  });

  it('shows today arrivals and departures counts', () => {
    renderDashboard();
    expect(screen.getByText('5')).toBeInTheDocument();  // todayArrivals
    expect(screen.getByText('3')).toBeInTheDocument();  // todayDepartures
    expect(screen.getByText('8')).toBeInTheDocument();  // availableRooms
    expect(screen.getByText('200')).toBeInTheDocument(); // totalGuests
  });

  it('shows pending revenue card for ADMIN', () => {
    renderDashboard();
    expect(screen.getByText('stat_pending_revenue')).toBeInTheDocument();
  });

  it('hides pending revenue card for RECEPTIONIST', () => {
    useAuthStore.setState({
      user: { sub: 'user2', username: 'reception', role: 'RECEPTIONIST' },
      isAuthenticated: true,
      isLoading: false,
    });
    useDashboardStore.setState({
      stats: { ...MOCK_STATS_ADMIN, pendingRevenue: null },
      isLoading: false,
      error: null,
      fetchStats: vi.fn(),
    });
    renderDashboard();
    expect(screen.queryByText('stat_pending_revenue')).not.toBeInTheDocument();
  });

  it('renders loading skeleton', () => {
    useDashboardStore.setState({ isLoading: true, stats: null, error: null, fetchStats: vi.fn() });
    const { container } = renderDashboard();
    expect(container.getElementsByClassName('animate-pulse').length).toBeGreaterThan(0);
  });

  it('renders error state with retry button', () => {
    useDashboardStore.setState({ error: 'FETCH_ERROR', stats: null, isLoading: false, fetchStats: vi.fn() });
    renderDashboard();
    expect(screen.getByText('FETCH_ERROR')).toBeInTheDocument();
    expect(screen.getByText('try_again')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = renderDashboard();
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
