import api from './api';
import type {
  GroupCheckoutOutcome,
  ReservationGroupCreateRequest,
  ReservationGroupResponse,
  SpringPage,
} from '../types';

const BASE_PATH = '/api/v1/reservation-groups';

export const reservationGroupService = {
  createGroup: async (request: ReservationGroupCreateRequest): Promise<ReservationGroupResponse> => {
    const response = await api.post<ReservationGroupResponse>(BASE_PATH, request);
    return response.data;
  },

  getGroup: async (id: string): Promise<ReservationGroupResponse> => {
    const response = await api.get<ReservationGroupResponse>(`${BASE_PATH}/${id}`);
    return response.data;
  },

  getAllGroups: async (page: number, size = 20): Promise<SpringPage<ReservationGroupResponse>> => {
    const response = await api.get<SpringPage<ReservationGroupResponse>>(
      `${BASE_PATH}?page=${page}&size=${size}`,
    );
    return response.data;
  },

  cancelGroup: async (id: string, version?: number | null): Promise<ReservationGroupResponse> => {
    const params = version === null || version === undefined ? '' : `?version=${version}`;
    const response = await api.post<ReservationGroupResponse>(`${BASE_PATH}/${id}/cancel${params}`);
    return response.data;
  },

  checkoutGroup: async (id: string): Promise<GroupCheckoutOutcome[]> => {
    const response = await api.post<GroupCheckoutOutcome[]>(`${BASE_PATH}/${id}/checkout`);
    return response.data;
  },
};
