import api from './api';
import type { RoomRequest, RoomResponse, RoomTypeRequest, RoomTypeResponse, RoomStatus } from '../types/inventory.types';
import type { SpringPage } from '../types/page.types';

const ROOM_TYPES_PATH = '/api/v1/room-types';
const ROOMS_PATH = '/api/v1/rooms';

export const inventoryService = {
  // Room Types
  getAllRoomTypes: async (): Promise<RoomTypeResponse[]> => {
    const response = await api.get<RoomTypeResponse[]>(ROOM_TYPES_PATH);
    return response.data;
  },

  getRoomTypeById: async (id: string): Promise<RoomTypeResponse> => {
    const response = await api.get<RoomTypeResponse>(`${ROOM_TYPES_PATH}/${id}`);
    return response.data;
  },

  createRoomType: async (data: RoomTypeRequest): Promise<RoomTypeResponse> => {
    const response = await api.post<RoomTypeResponse>(ROOM_TYPES_PATH, data);
    return response.data;
  },

  updateRoomType: async (id: string, data: RoomTypeRequest): Promise<RoomTypeResponse> => {
    const response = await api.put<RoomTypeResponse>(`${ROOM_TYPES_PATH}/${id}`, data);
    return response.data;
  },

  deleteRoomType: async (id: string): Promise<void> => {
    await api.delete(`${ROOM_TYPES_PATH}/${id}`);
  },

  // Rooms
  getAllRooms: async (page = 0, size = 100): Promise<SpringPage<RoomResponse>> => {
    const response = await api.get<SpringPage<RoomResponse>>(`${ROOMS_PATH}?page=${page}&size=${size}&sort=roomNumber,asc`);
    return response.data;
  },

  getRoomById: async (id: string): Promise<RoomResponse> => {
    const response = await api.get<RoomResponse>(`${ROOMS_PATH}/${id}`);
    return response.data;
  },

  createRoom: async (data: RoomRequest): Promise<RoomResponse> => {
    // Hotel ID is required but often we mock it in single-tenant setups if not provided by auth
    const payload = { ...data, hotelId: data.hotelId || '00000000-0000-0000-0000-000000000000' };
    const response = await api.post<RoomResponse>(ROOMS_PATH, payload);
    return response.data;
  },

  updateRoom: async (id: string, data: RoomRequest): Promise<RoomResponse> => {
    const payload = { ...data, hotelId: data.hotelId || '00000000-0000-0000-0000-000000000000' };
    const response = await api.put<RoomResponse>(`${ROOMS_PATH}/${id}`, payload);
    return response.data;
  },

  updateRoomStatus: async (id: string, status: RoomStatus): Promise<RoomResponse> => {
    const response = await api.patch<RoomResponse>(`${ROOMS_PATH}/${id}/status`, `"${status}"`, {
      headers: { 'Content-Type': 'application/json' }
    });
    return response.data;
  },

  deleteRoom: async (id: string): Promise<void> => {
    await api.delete(`${ROOMS_PATH}/${id}`);
  }
};
