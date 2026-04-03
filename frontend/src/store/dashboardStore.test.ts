import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useDashboardStore } from './dashboardStore';
import { dashboardService } from '../services/dashboardService';

vi.mock('../services/dashboardService', () => ({
  dashboardService: {
    getDashboardStats: vi.fn(),
  },
}));

describe('dashboardStore', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      stats: null,
      isLoading: false,
      error: null,
    });
    vi.clearAllMocks();
  });

  it('should fetch stats successfully', async () => {
    const mockStats = {
      totalGuests: 150,
      activeReservationsPercentage: 75.5,
      currentStaysPercentage: 45.2,
      pendingRevenue: 5000,
    };
    vi.mocked(dashboardService.getDashboardStats).mockResolvedValueOnce(mockStats);

    const store = useDashboardStore.getState();
    
    const fetchPromise = store.fetchStats(); // Trigger fetch
    
    // Intermediate state check
    expect(useDashboardStore.getState().isLoading).toBe(true);
    expect(useDashboardStore.getState().error).toBeNull();
    
    await fetchPromise; // Wait for fetch to complete
    
    // Final state check
    expect(useDashboardStore.getState().stats).toEqual(mockStats);
    expect(useDashboardStore.getState().isLoading).toBe(false);
    expect(useDashboardStore.getState().error).toBeNull();
    expect(dashboardService.getDashboardStats).toHaveBeenCalledTimes(1);
  });

  it('should handle fetch stats error', async () => {
    const errorMessage = 'ERROR_FETCH_DASHBOARD';
    vi.mocked(dashboardService.getDashboardStats).mockRejectedValueOnce({
      response: { data: { message: errorMessage } },
    });

    const store = useDashboardStore.getState();
    await store.fetchStats();
    
    expect(useDashboardStore.getState().stats).toBeNull();
    expect(useDashboardStore.getState().isLoading).toBe(false);
    expect(useDashboardStore.getState().error).toBe(errorMessage);
  });
});
