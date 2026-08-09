import api from './api';
import type {
  RateSeasonRequest,
  RateSeasonResponse,
  RateCalendarResponse,
  RateBulkApplyRequest,
} from '../types/inventory.types';

const rateSeasonsPath = (roomTypeId: string) => `/api/v1/room-types/${roomTypeId}/rate-seasons`;
const RATE_CALENDAR_PATH = '/api/v1/rate-calendar';

export const rateSeasonService = {
  getRateCalendar: async (from: string, to: string): Promise<RateCalendarResponse> => {
    const response = await api.get<RateCalendarResponse>(RATE_CALENDAR_PATH, { params: { from, to } });
    return response.data;
  },

  bulkApplyRate: async (data: RateBulkApplyRequest): Promise<RateSeasonResponse[]> => {
    const response = await api.post<RateSeasonResponse[]>(`${RATE_CALENDAR_PATH}/bulk-apply`, data);
    return response.data;
  },

  listSeasons: async (roomTypeId: string): Promise<RateSeasonResponse[]> => {
    const response = await api.get<RateSeasonResponse[]>(rateSeasonsPath(roomTypeId));
    return response.data;
  },

  createSeason: async (roomTypeId: string, data: RateSeasonRequest): Promise<RateSeasonResponse> => {
    const response = await api.post<RateSeasonResponse>(rateSeasonsPath(roomTypeId), data);
    return response.data;
  },

  updateSeason: async (roomTypeId: string, id: string, data: RateSeasonRequest): Promise<RateSeasonResponse> => {
    const response = await api.put<RateSeasonResponse>(`${rateSeasonsPath(roomTypeId)}/${id}`, data);
    return response.data;
  },

  deleteSeason: async (roomTypeId: string, id: string): Promise<void> => {
    await api.delete(`${rateSeasonsPath(roomTypeId)}/${id}`);
  },
};
