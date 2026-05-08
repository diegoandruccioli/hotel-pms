import axios from 'axios';

// Base Axios instance
const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '',
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

import { useAuthStore } from '../store/authStore';
import i18n from '../i18n';

/**
 * Reads the non-httpOnly csrf_token cookie set by auth-service on login/refresh.
 * Returns null if the cookie is absent (pre-login state).
 */
function getCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)csrf_token=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

// Request interceptor: inject X-CSRF-Token header on mutating requests (T-GW-05)
api.interceptors.request.use((config) => {
  const method = config.method?.toUpperCase();
  if (method && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
    const token = getCsrfToken();
    if (token) {
      config.headers['X-CSRF-Token'] = token;
    }
  }
  return config;
});

// Queue of callbacks waiting for a token refresh to complete
let isRefreshing = false;
const pendingQueue: Array<{
  resolve: () => void;
  reject: (err: unknown) => void;
}> = [];

function drainQueue(success: boolean, err: unknown): void {
  for (const item of pendingQueue) {
    if (success) {
      item.resolve();
    } else {
      item.reject(err);
    }
  }
  pendingQueue.length = 0;
}

function performLogout(): void {
  useAuthStore.getState().logout();
  window.location.href = '/login';
}

// Response interceptor: translate error codes and handle 401 with silent refresh (T-AUTH-04)
api.interceptors.response.use(
  (response) => {
    return response;
  },
  async (error) => {
    // Translate UPPER_SNAKE_CASE error codes from backend
    if (error.response?.data?.detail && /^[A-Z_]+$/.test(error.response.data.detail)) {
      const code = error.response.data.detail;
      const translated = i18n.t(`errors:${code}`);
      if (translated !== `errors:${code}`) {
        error.response.data.detail = translated;
      }
    }

    if (error.response?.status === 401) {
      const url: string = error.config?.url ?? '';

      // Never attempt refresh on auth endpoints themselves to prevent loops
      if (url.includes('/login') || url.includes('/me')) {
        return Promise.reject(error);
      }
      if (url.includes('/refresh')) {
        // Refresh endpoint returned 401 — session is truly expired
        performLogout();
        return Promise.reject(error);
      }

      const originalConfig = error.config;
      if (!originalConfig) {
        return Promise.reject(error);
      }

      if (isRefreshing) {
        // Another refresh is already in progress — queue this request
        return new Promise<void>((resolve, reject) => {
          pendingQueue.push({ resolve, reject });
        })
          .then(() => api(originalConfig))
          .catch(() => Promise.reject(error));
      }

      isRefreshing = true;

      try {
        await api.post('/api/v1/auth/refresh');
        drainQueue(true, null);
        return api(originalConfig);
      } catch (refreshError) {
        drainQueue(false, refreshError);
        performLogout();
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export default api;
