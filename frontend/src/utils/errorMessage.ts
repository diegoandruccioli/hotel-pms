import axios from 'axios';

/**
 * Extracts a user-facing message from a caught error, preferring the backend's
 * `detail` field (already translated by the Axios response interceptor when it
 * was an `[A-Z_]+` error code — see `services/api.ts`) over `err.message`, which
 * for an `AxiosError` is a generic string like "Request failed with status code 409"
 * that carries no information about what actually went wrong.
 *
 * @param err      the value caught in a try/catch around an API call
 * @param fallback i18n-translated fallback shown when no `detail` is present
 */
export function getErrorMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const detail: unknown = err.response?.data?.detail;
    if (typeof detail === 'string' && detail.trim() !== '') {
      return detail;
    }
  }
  return fallback;
}
