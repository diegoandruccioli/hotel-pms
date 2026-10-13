import { todayIsoDate } from '../utils';
import api from './api';
import type { DaySheetResponse, DaySheetTrendResponse } from '../types';

const DAY_SHEET_PATH = '/api/v1/frontdesk/day-sheet';
const DAY_SHEET_TREND_PATH = `${DAY_SHEET_PATH}/trend`;
const DEFAULT_TREND_DAYS = 7;

export const dashboardService = {
  /**
   * Fetches the front-desk day-sheet summary for today in a single request —
   * replaces what used to be four unpaginated client calls (reservations,
   * stays, rooms, availability) aggregated in the browser (E-DASHBOARD-1).
   */
  getDaySheet: async (): Promise<DaySheetResponse> => {
    const response = await api.get<DaySheetResponse>(DAY_SHEET_PATH, {
      params: { date: todayIsoDate() },
    });
    return response.data;
  },

  /**
   * Fetches the night-audit snapshots of the `days` dates before today (1–14),
   * for deltas and sparklines. Resolves to `null` on 404 so a backend that
   * predates the endpoint degrades to "no trend" instead of an error state.
   */
  getDaySheetTrend: async (days: number = DEFAULT_TREND_DAYS): Promise<DaySheetTrendResponse | null> => {
    const response = await api.get<DaySheetTrendResponse>(DAY_SHEET_TREND_PATH, {
      params: { date: todayIsoDate(), days },
      validateStatus: (status) => status === 200 || status === 404,
    });
    return response.status === 404 ? null : response.data;
  },
};
