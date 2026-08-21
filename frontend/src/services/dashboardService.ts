import api from './api';
import type { DaySheetResponse } from '../types/daySheet.types';

const DAY_SHEET_PATH = '/api/v1/frontdesk/day-sheet';

const getTodayDateString = (): string => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

export const dashboardService = {
  /**
   * Fetches the front-desk day-sheet summary for today in a single request —
   * replaces what used to be four unpaginated client calls (reservations,
   * stays, rooms, availability) aggregated in the browser (E-DASHBOARD-1).
   */
  getDaySheet: async (): Promise<DaySheetResponse> => {
    const response = await api.get<DaySheetResponse>(DAY_SHEET_PATH, {
      params: { date: getTodayDateString() },
    });
    return response.data;
  },
};
