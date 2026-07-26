import { AxiosError } from 'axios';

/**
 * Builds an AxiosError carrying a `response.data.detail`, the shape produced by the
 * backend's `@ControllerAdvice` (and already translated by the Axios response
 * interceptor for `[A-Z_]+` codes — see `services/api.ts`). Used to test that
 * components surface `detail` via `utils/errorMessage.ts` instead of the generic
 * `err.message` string Axios itself would produce (e.g. "Request failed with
 * status code 409").
 */
export function mockAxiosErrorWithDetail(detail: string, status = 500): AxiosError {
  return new AxiosError(`Request failed with status code ${status}`, 'ERR_BAD_REQUEST', undefined, undefined, {
    status,
    statusText: 'Error',
    headers: {},
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    config: {} as any,
    data: { detail },
  });
}
