import { create } from 'zustand';
import { dashboardService } from '../services/dashboardService';
import type { DashboardStats } from '../services/dashboardService';

interface DashboardState {
  stats: DashboardStats | null;
  isLoading: boolean;
  error: string | null;
  fetchStats: () => Promise<void>;
}

export const useDashboardStore = create<DashboardState>((set) => ({
  stats: null,
  isLoading: false,
  error: null,
  fetchStats: async () => {
    set({ isLoading: true, error: null });
    try {
      const stats = await dashboardService.getDashboardStats();
      set({ stats, isLoading: false });
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string, detail?: string } } };
      set({ 
        error: e.response?.data?.message || e.response?.data?.detail || 'ERROR_FETCH_DASHBOARD', 
        isLoading: false 
      });
    }
  },
}));
