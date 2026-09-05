import api from './api';
import type { NightAuditRunResponse, SpringPage } from '../types';

const BASE_PATH = '/api/v1/frontdesk/night-audit';

export const nightAuditService = {
  run: async (date: string): Promise<NightAuditRunResponse> => {
    const response = await api.post<NightAuditRunResponse>(`${BASE_PATH}?date=${date}`);
    return response.data;
  },

  getHistory: async (page: number, size = 20): Promise<SpringPage<NightAuditRunResponse>> => {
    const response = await api.get<SpringPage<NightAuditRunResponse>>(
      `${BASE_PATH}?page=${page}&size=${size}&sort=businessDate,desc`,
    );
    return response.data;
  },
};
