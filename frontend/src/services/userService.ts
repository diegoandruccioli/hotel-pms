import api from './api';
import type { UserResponse, CreateUserRequest } from '../types/user.types';

const BASE_PATH = '/api/v1/auth/users';

export const userService = {
  listUsers: async (): Promise<UserResponse[]> => {
    const response = await api.get<UserResponse[]>(BASE_PATH);
    return response.data;
  },

  createUser: async (data: CreateUserRequest): Promise<UserResponse> => {
    const response = await api.post<UserResponse>(BASE_PATH, data);
    return response.data;
  },

  deactivateUser: async (userId: string): Promise<UserResponse> => {
    const response = await api.patch<UserResponse>(`${BASE_PATH}/${userId}/deactivate`);
    return response.data;
  },

  activateUser: async (userId: string): Promise<UserResponse> => {
    const response = await api.patch<UserResponse>(`${BASE_PATH}/${userId}/activate`);
    return response.data;
  },
};
