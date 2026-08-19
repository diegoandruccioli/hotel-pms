import { QueryClient } from '@tanstack/react-query';
import axios from 'axios';

/**
 * Shared TanStack Query client. Sits ON TOP of the existing Axios instance
 * (`services/api.ts`) — it never replaces it. `queryFn`/`mutationFn`
 * implementations call the existing `services/*.ts` modules, so the CSRF
 * header injection, the single-flight silent-refresh-on-401 queue, and the
 * i18n translation of backend error codes in `api.ts` all keep working
 * unchanged; React Query only adds caching, deduplication and invalidation
 * on top of that transport.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // A 401 is already handled by the Axios response interceptor, which
      // transparently retries the original request once after a silent
      // refresh. Retrying it again here would race a second refresh attempt
      // against the interceptor's own queue. Non-401 4xx are the caller's
      // input being wrong, not a transient failure — retrying does not help.
      retry: (failureCount, error) => {
        if (axios.isAxiosError(error) && error.response && error.response.status < 500) {
          return false;
        }
        return failureCount < 2;
      },
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: false,
    },
  },
});
