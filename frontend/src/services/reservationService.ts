import api from './api';
import type { ReservationRequest, ReservationResponse } from '../types';
import type { SpringPage } from '../types';
import { downloadViaIframe } from '../utils/downloadViaIframe';

const BASE_PATH = '/api/v1/reservations';

export const reservationService = {
  getAllReservations: async (): Promise<ReservationResponse[]> => {
    const response = await api.get<SpringPage<ReservationResponse>>(`${BASE_PATH}?size=500`);
    return response.data.content;
  },

  searchReservations: async (params: {
    query?: string;
    upcomingOnly?: boolean;
    page?: number;
    size?: number;
    sort?: string;
  }): Promise<SpringPage<ReservationResponse>> => {
    const searchParams = new URLSearchParams({
      page: String(params.page ?? 0),
      size: String(params.size ?? 20),
    });
    if (params.query?.trim()) searchParams.set('query', params.query.trim());
    if (params.upcomingOnly) searchParams.set('upcomingOnly', 'true');
    if (params.sort) searchParams.set('sort', params.sort);
    const response = await api.get<SpringPage<ReservationResponse>>(
      `${BASE_PATH}/search?${searchParams.toString()}`,
    );
    return response.data;
  },

  getReservationById: async (id: string): Promise<ReservationResponse> => {
    const response = await api.get<ReservationResponse>(`${BASE_PATH}/${id}`);
    return response.data;
  },

  createReservation: async (data: ReservationRequest): Promise<ReservationResponse> => {
    const response = await api.post<ReservationResponse>(BASE_PATH, data);
    return response.data;
  },

  updateReservation: async (id: string, data: Partial<ReservationRequest>): Promise<ReservationResponse> => {
    const response = await api.put<ReservationResponse>(`${BASE_PATH}/${id}`, data);
    return response.data;
  },

  deleteReservation: async (id: string): Promise<void> => {
    await api.delete(`${BASE_PATH}/${id}`);
  },

  updateStatus: async (
    id: string,
    status: string,
    version: number,
    actualGuests?: number | null,
  ): Promise<ReservationResponse> => {
    const response = await api.patch<ReservationResponse>(`${BASE_PATH}/${id}/status-and-guests`, {
      status,
      actualGuests: actualGuests ?? null,
      version,
    });
    return response.data;
  },

  retryConfirmationEmail: async (id: string): Promise<ReservationResponse> => {
    const response = await api.post<ReservationResponse>(`${BASE_PATH}/${id}/confirmation-email/retry`, {});
    return response.data;
  },

  /** Downloads every reservation matching the same filters as {@link searchReservations}
   * as a CSV file via a hidden iframe. */
  exportReservationsCsv: async (params: {
    query?: string;
    upcomingOnly?: boolean;
    dateFrom?: string;
    dateTo?: string;
    status?: string;
  }): Promise<void> => {
    const searchParams = new URLSearchParams();
    if (params.query?.trim()) searchParams.set('query', params.query.trim());
    if (params.upcomingOnly) searchParams.set('upcomingOnly', 'true');
    if (params.dateFrom) searchParams.set('dateFrom', params.dateFrom);
    if (params.dateTo) searchParams.set('dateTo', params.dateTo);
    if (params.status) searchParams.set('status', params.status);
    const qs = searchParams.toString();
    await downloadViaIframe(`${BASE_PATH}/export.csv${qs ? `?${qs}` : ''}`);
  },
};
