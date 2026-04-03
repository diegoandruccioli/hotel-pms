import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Dashboard } from './Dashboard';
import { axe } from 'vitest-axe';
import { useDashboardStore } from '../store/dashboardStore';
import { useAuthStore } from '../store/authStore';

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: { name?: string }) => {
      if (key === 'welcome_back' && options?.name) {
        return `welcome_back ${options.name}`;
      }
      return key;
    },
  }),
  initReactI18next: {
    type: '3rdParty',
    init: vi.fn(),
  }
}));

describe('Dashboard Component', () => {
  beforeEach(() => {
    // Reset Zustand stores
    useDashboardStore.setState({
      stats: {
        totalGuests: 200,
        activeReservationsPercentage: 80,
        currentStaysPercentage: 50,
        pendingRevenue: 10000,
      },
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

  it('renders dashboard with stats', () => {
    render(<Dashboard />);
    expect(screen.getByTestId('dashboard-page')).toBeInTheDocument();
    expect(screen.getByTestId('dashboard-heading')).toHaveTextContent('welcome_back admin');
    expect(screen.getByTestId('stats-grid')).toBeInTheDocument();
    
    // Check if stats are rendered
    expect(screen.getByText('200')).toBeInTheDocument(); // total guests
    expect(screen.getByText('80.00%')).toBeInTheDocument(); // active reservations percentage
    expect(screen.getByText('50.00%')).toBeInTheDocument(); // current stays percentage
    expect(screen.getByText('$10,000.00')).toBeInTheDocument(); // pending revenue
  });

  it('renders loading state correctly', () => {
    useDashboardStore.setState({ isLoading: true, stats: null });
    const { container } = render(<Dashboard />);
    // Stats elements won't display numbers, rendering logic shows skeletons
    expect(container.getElementsByClassName('animate-pulse').length).toBeGreaterThan(0);
  });

  it('renders error state correctly', () => {
    useDashboardStore.setState({ error: 'FETCH_ERROR', stats: null, isLoading: false });
    render(<Dashboard />);
    expect(screen.getByText('FETCH_ERROR')).toBeInTheDocument();
    expect(screen.getByText('try_again')).toBeInTheDocument();
  });

  it('should have no accessibility violations', async () => {
    const { container } = render(<Dashboard />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
