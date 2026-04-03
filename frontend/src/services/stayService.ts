import api from './api';
import type { StayRequest, StayResponse } from '../types/stay.types';
import type { SpringPage } from '../types/page.types';

const BASE_PATH = '/api/v1/stays';

export const stayService = {
  getAllStays: async (): Promise<StayResponse[]> => {
    const response = await api.get<SpringPage<StayResponse>>(BASE_PATH);
    return response.data.content;
  },

  getStayById: async (id: string): Promise<StayResponse> => {
    const response = await api.get<StayResponse>(`${BASE_PATH}/${id}`);
    return response.data;
  },

  createStay: async (data: StayRequest): Promise<StayResponse> => {
    const response = await api.post<StayResponse>(BASE_PATH, data);
    return response.data;
  },

  updateStay: async (id: string, data: StayRequest): Promise<StayResponse> => {
    const response = await api.put<StayResponse>(`${BASE_PATH}/${id}`, data);
    return response.data;
  },

  extendStay: async (id: string, newCheckOutDate: string): Promise<StayResponse> => {
    const response = await api.put<StayResponse>(`${BASE_PATH}/${id}/extend`, { newCheckOutDate });
    return response.data;
  },

  checkOut: async (id: string): Promise<StayResponse> => {
    const response = await api.put<StayResponse>(`${BASE_PATH}/${id}/check-out`, {});
    return response.data;
  },

  downloadAlloggiatiReport: async (date: string): Promise<void> => {
    const response = await api.get(`${BASE_PATH}/reports/alloggiati`, {
      params: { date },
      responseType: 'blob',
    });
    const blob = new Blob([response.data as BlobPart], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `alloggiati-${date}.txt`;
    link.click();
    URL.revokeObjectURL(url);
  },
};
