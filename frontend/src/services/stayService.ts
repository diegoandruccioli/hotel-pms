import api from './api';
import type {
  AlloggiatiComune,
  AlloggiatiFailureSummaryResponse,
  AlloggiatiStato,
  AlloggiatiTipdoc,
  AvailableRoom,
  HotelSettingsRequest,
  HotelSettingsResponse,
  StayRequest,
  StayResponse,
} from '../types/stay.types';
import type { SpringPage } from '../types/page.types';

const BASE_PATH = '/api/v1/stays';

export const stayService = {
  getAllStays: async (page = 0, size = 20): Promise<SpringPage<StayResponse>> => {
    const response = await api.get<SpringPage<StayResponse>>(
      `${BASE_PATH}?page=${page}&size=${size}&sort=actualCheckInTime,desc`,
    );
    return response.data;
  },

  getStayById: async (id: string): Promise<StayResponse> => {
    const response = await api.get<StayResponse>(`${BASE_PATH}/${id}`);
    return response.data;
  },

  createStay: async (data: StayRequest): Promise<StayResponse> => {
    const response = await api.post<StayResponse>(BASE_PATH, data);
    return response.data;
  },

  checkOut: async (id: string): Promise<StayResponse> => {
    const response = await api.put<StayResponse>(`${BASE_PATH}/${id}/check-out`, {});
    return response.data;
  },

  retryInvoiceCreation: async (id: string): Promise<StayResponse> => {
    const response = await api.post<StayResponse>(`${BASE_PATH}/${id}/invoice/retry`, {});
    return response.data;
  },

  retryCheckoutEmail: async (id: string): Promise<StayResponse> => {
    const response = await api.post<StayResponse>(`${BASE_PATH}/${id}/checkout-email/retry`, {});
    return response.data;
  },

  getHotelSettings: async (): Promise<HotelSettingsResponse> => {
    const response = await api.get<HotelSettingsResponse>(`${BASE_PATH}/settings`);
    return response.data;
  },

  updateHotelSettings: async (data: HotelSettingsRequest): Promise<HotelSettingsResponse> => {
    const response = await api.put<HotelSettingsResponse>(`${BASE_PATH}/settings`, data);
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

  getLastCompletedStayForGuest: async (guestId: string): Promise<StayResponse | null> => {
    const response = await api.get<StayResponse>(`${BASE_PATH}/guest/${guestId}/latest`, {
      validateStatus: (s) => s === 200 || s === 204,
    });
    return response.status === 204 ? null : response.data;
  },

  downloadAlloggiatiJson: async (date: string): Promise<void> => {
    const response = await api.get(`${BASE_PATH}/reports/alloggiati/json`, {
      params: { date },
      responseType: 'blob',
    });
    const blob = new Blob([response.data as BlobPart], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `alloggiati-${date}.json`;
    link.click();
    URL.revokeObjectURL(url);
  },

  getLookupStati: async (): Promise<AlloggiatiStato[]> => {
    const response = await api.get<AlloggiatiStato[]>(`${BASE_PATH}/lookup/stati`);
    return response.data;
  },

  searchLookupComuni: async (q: string, provincia?: string): Promise<AlloggiatiComune[]> => {
    const response = await api.get<AlloggiatiComune[]>(`${BASE_PATH}/lookup/comuni`, {
      params: { q, provincia },
    });
    return response.data;
  },

  submitAlloggiatiReport: async (date: string): Promise<void> => {
    await api.post(`${BASE_PATH}/reports/alloggiati/submit`, null, { params: { date } });
  },

  getLookupTipdoc: async (): Promise<AlloggiatiTipdoc[]> => {
    const response = await api.get<AlloggiatiTipdoc[]>(`${BASE_PATH}/lookup/tipdoc`);
    return response.data;
  },

  getAlloggiatiFailureSummary: async (): Promise<AlloggiatiFailureSummaryResponse> => {
    const response = await api.get<AlloggiatiFailureSummaryResponse>(
      `${BASE_PATH}/reports/alloggiati/failures/summary`,
    );
    return response.data;
  },

  getAvailableRooms: async (): Promise<AvailableRoom[]> => {
    const response = await api.get<{ content: AvailableRoom[] }>('/api/v1/rooms', {
      params: { size: 200 },
    });
    return (response.data.content ?? []).filter((r) => r.status === 'CLEAN');
  },
};
