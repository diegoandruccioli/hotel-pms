import { create } from 'zustand';
import type { UserPayload } from '../types/auth.types';

interface AuthState {
  user: UserPayload | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (user: UserPayload) => void;
  logout: () => void;
  checkAuth: (user: UserPayload | null) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,
  isLoading: true, // Initially true while checking session on load

  login: (user: UserPayload) => {
    set({
      user,
      isAuthenticated: true,
    });
  },

  logout: () => {
    set({
      user: null,
      isAuthenticated: false,
    });
  },

  checkAuth: (user: UserPayload | null) => {
    set({
      user,
      isAuthenticated: !!user,
      isLoading: false,
    });
  },
}));
