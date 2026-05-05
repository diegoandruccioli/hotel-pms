import api from './api';
import type { LoginRequest, RegisterRequest, UserPayload } from '../types/auth.types';

export const authService = {
  login: async (data: LoginRequest): Promise<{ mustChangePassword: boolean }> => {
    const response = await api.post<{ mustChangePassword: boolean }>('/api/v1/auth/login', data);
    return response.data ?? { mustChangePassword: false };
  },

  register: async (data: RegisterRequest): Promise<void> => {
    await api.post('/api/v1/auth/register', data);
  },

  logout: async (): Promise<void> => {
    await api.post('/api/v1/auth/logout');
  },

  fetchMe: async (): Promise<UserPayload> => {
    const response = await api.get<UserPayload>('/api/v1/auth/me');
    return response.data;
  },

  changePassword: async (data: { currentPassword: string; newPassword: string }): Promise<void> => {
    await api.post('/api/v1/auth/change-password', data);
  },
};
