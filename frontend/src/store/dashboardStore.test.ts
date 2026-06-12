import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useDashboardStore } from './dashboardStore';
import { dashboardService } from '../services/dashboardService';

vi.mock('../services/dashboardService', () => ({
  dashboardService: {
    getDashboardStats: vi.fn(),
  },
}));

const MOCK_STATS_OWNER = {
  totalGuests: 150,
  todayArrivals: 3,
  todayDepartures: 2,
  currentStays: 10,
  availableRooms: 5,
  pendingRevenue: 5000,
  rooms: [],
};

const MOCK_STATS_RECEPTIONIST = {
  totalGuests: 50,
  todayArrivals: 1,
  todayDepartures: 0,
  currentStays: 4,
  availableRooms: 3,
  pendingRevenue: null,
  rooms: [],
};

describe('dashboardStore', () => {
  beforeEach(() => {
    useDashboardStore.setState({ stats: null, isLoading: false, error: null });
    vi.clearAllMocks();
  });

  it('fetches stats successfully for OWNER role', async () => {
    vi.mocked(dashboardService.getDashboardStats).mockResolvedValueOnce(MOCK_STATS_OWNER);

    const fetchPromise = useDashboardStore.getState().fetchStats(true);

    expect(useDashboardStore.getState().isLoading).toBe(true);
    expect(useDashboardStore.getState().error).toBeNull();

    await fetchPromise;

    expect(useDashboardStore.getState().stats).toEqual(MOCK_STATS_OWNER);
    expect(useDashboardStore.getState().isLoading).toBe(false);
    expect(useDashboardStore.getState().error).toBeNull();
    expect(dashboardService.getDashboardStats).toHaveBeenCalledWith(true);
  });

  it('fetches stats with pendingRevenue=null for RECEPTIONIST role', async () => {
    vi.mocked(dashboardService.getDashboardStats).mockResolvedValueOnce(MOCK_STATS_RECEPTIONIST);

    await useDashboardStore.getState().fetchStats(false);

    expect(useDashboardStore.getState().stats?.pendingRevenue).toBeNull();
    expect(dashboardService.getDashboardStats).toHaveBeenCalledWith(false);
  });

  it('handles fetch error with response message', async () => {
    vi.mocked(dashboardService.getDashboardStats).mockRejectedValueOnce({
      response: { data: { message: 'ERROR_FETCH_DASHBOARD' } },
    });

    await useDashboardStore.getState().fetchStats(false);

    expect(useDashboardStore.getState().stats).toBeNull();
    expect(useDashboardStore.getState().isLoading).toBe(false);
    expect(useDashboardStore.getState().error).toBe('ERROR_FETCH_DASHBOARD');
  });

  it('falls back to generic error key when no response message', async () => {
    vi.mocked(dashboardService.getDashboardStats).mockRejectedValueOnce(new Error('timeout'));

    await useDashboardStore.getState().fetchStats(true);

    expect(useDashboardStore.getState().error).toBe('ERROR_FETCH_DASHBOARD');
  });
});
