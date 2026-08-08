import api from './api';
import type { RateSeasonRequest, RateSeasonResponse } from '../types/inventory.types';

const rateSeasonsPath = (roomTypeId: string) => `/api/v1/room-types/${roomTypeId}/rate-seasons`;

export const rateSeasonService = {
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
