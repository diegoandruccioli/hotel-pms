import api from './api';
import type { LoginRequest, RegisterRequest, UserPayload } from '../types/auth.types';

export const authService = {
  login: async (data: LoginRequest): Promise<void> => {
    await api.post('/api/v1/auth/login', data);
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
  }
};
