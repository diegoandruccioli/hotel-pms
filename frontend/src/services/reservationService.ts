import api from './api';
import type { ReservationRequest, ReservationResponse } from '../types/reservation.types';
import type { SpringPage } from '../types/page.types';

const BASE_PATH = '/api/v1/reservations';

export const reservationService = {
  getAllReservations: async (): Promise<ReservationResponse[]> => {
    const response = await api.get<SpringPage<ReservationResponse>>(`${BASE_PATH}?size=500`);
    return response.data.content;
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
};
