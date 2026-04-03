import api from './api';
import type { GuestRequestDTO, GuestResponseDTO } from '../types/guest.types';
import type { SpringPage } from '../types/page.types';

const BASE_PATH = '/api/v1/guests';

export const guestService = {
  getAllGuests: async (): Promise<GuestResponseDTO[]> => {
    const response = await api.get<SpringPage<GuestResponseDTO>>(BASE_PATH);
    return response.data.content;
  },

  searchGuests: async (query: string): Promise<GuestResponseDTO[]> => {
    const trimmed = query.trim();
    const url = trimmed ? `${BASE_PATH}/search?query=${encodeURIComponent(trimmed)}` : BASE_PATH;
    const response = await api.get<SpringPage<GuestResponseDTO>>(url);
    return response.data.content;
  },

  getGuestById: async (id: string): Promise<GuestResponseDTO> => {
    const response = await api.get<GuestResponseDTO>(`${BASE_PATH}/${id}`);
    return response.data;
  },

  createGuest: async (data: GuestRequestDTO): Promise<GuestResponseDTO> => {
    const response = await api.post<GuestResponseDTO>(BASE_PATH, data);
    return response.data;
  },

  updateGuest: async (id: string, data: GuestRequestDTO): Promise<GuestResponseDTO> => {
    const response = await api.put<GuestResponseDTO>(`${BASE_PATH}/${id}`, data);
    return response.data;
  },

  deleteGuest: async (id: string): Promise<void> => {
    await api.delete(`${BASE_PATH}/${id}`);
  }
};
