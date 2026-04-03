import axios from 'axios';

// Base Axios instance
const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080', // Replace with Gateway URL later
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

import { useAuthStore } from '../store/authStore';
import i18n from '../i18n';

// Response interceptor for handling 401s (token expiration) globaly
api.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    // Translate UPPER_SNAKE_CASE error codes from backend
    if (error.response?.data?.detail && /^[A-Z_]+$/.test(error.response.data.detail)) {
      const code = error.response.data.detail;
      const translated = i18n.t(`errors:${code}`);
      if (translated !== `errors:${code}`) {
        error.response.data.detail = translated;
      }
    }

    if (error.response?.status === 401) {
      const url = error.config?.url;
      // Do not trigger global logout/redirect if the 401 is from login or me endpoints
      if (url && (url.includes('/login') || url.includes('/me'))) {
        return Promise.reject(error);
      }
      console.warn('Unauthorized access, logging out and redirecting to login');
      // Trigger logout via Zustand which clears token
      useAuthStore.getState().logout();
      // Need a full page reload or router redirection; 
      // simple window location works if we are not inside a router context
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
