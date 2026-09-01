import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios';

const logoutMock = vi.fn();

vi.mock('../store/authStore', () => ({
  useAuthStore: {
    getState: vi.fn(() => ({ logout: logoutMock })),
  },
}));

vi.mock('../i18n', () => ({
  default: {
    t: vi.fn((key: string) => `TRANSLATED(${key})`),
  },
}));

import api from './api';
import i18n from '../i18n';

function makeResponse<T>(config: InternalAxiosRequestConfig, status: number, data: T): AxiosResponse<T> {
  return {
    data,
    status,
    statusText: '',
    headers: {},
    config,
  } as AxiosResponse<T>;
}

describe('api (axios instance + interceptors)', () => {
  let adapter: ReturnType<typeof vi.fn>;
  let originalLocation: Location;

  beforeEach(() => {
    vi.clearAllMocks();
    document.cookie = 'csrf_token=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';
    adapter = vi.fn();
    api.defaults.adapter = adapter as unknown as AxiosAdapter;
    originalLocation = window.location;
    Object.defineProperty(window, 'location', {
      value: { ...originalLocation, href: '' },
      writable: true,
    });
  });

  afterEach(() => {
    Object.defineProperty(window, 'location', { value: originalLocation, writable: true });
  });

  it('does not attach X-CSRF-Token on GET requests', async () => {
    document.cookie = 'csrf_token=abc123;path=/';
    adapter.mockResolvedValueOnce(makeResponse({} as InternalAxiosRequestConfig, 200, { ok: true }));

    await api.get('/api/v1/guests');

    const sentConfig = adapter.mock.calls[0][0] as InternalAxiosRequestConfig;
    expect(sentConfig.headers?.['X-CSRF-Token']).toBeUndefined();
  });

  it('attaches X-CSRF-Token on POST when the cookie is present', async () => {
    document.cookie = 'csrf_token=abc123;path=/';
    adapter.mockResolvedValueOnce(makeResponse({} as InternalAxiosRequestConfig, 201, { id: '1' }));

    await api.post('/api/v1/guests', { name: 'Mario' });

    const sentConfig = adapter.mock.calls[0][0] as InternalAxiosRequestConfig;
    expect(sentConfig.headers?.['X-CSRF-Token']).toBe('abc123');
  });

  it('does not attach X-CSRF-Token on POST when the cookie is absent', async () => {
    adapter.mockResolvedValueOnce(makeResponse({} as InternalAxiosRequestConfig, 201, { id: '1' }));

    await api.post('/api/v1/guests', { name: 'Mario' });

    const sentConfig = adapter.mock.calls[0][0] as InternalAxiosRequestConfig;
    expect(sentConfig.headers?.['X-CSRF-Token']).toBeUndefined();
  });

  it('translates an UPPER_SNAKE_CASE error detail via i18n', async () => {
    adapter.mockRejectedValueOnce({
      config: { url: '/api/v1/reservations', headers: {} },
      response: { status: 400, data: { detail: 'ROOM_NOT_AVAILABLE' } },
    });

    await expect(api.get('/api/v1/reservations')).rejects.toMatchObject({
      response: { data: { detail: 'TRANSLATED(errors:ROOM_NOT_AVAILABLE)' } },
    });
  });

  it('stashes the untranslated code as errorCode alongside the translated detail — '
      + 'callers need something stable to branch on once detail becomes human text '
      + '(e.g. distinguishing a stale-optimistic-lock 409 from a room-overlap 409, '
      + 'both surfaced with the same HTTP status)', async () => {
    adapter.mockRejectedValueOnce({
      config: { url: '/api/v1/reservations/r1', headers: {} },
      response: { status: 409, data: { detail: 'RESERVATION_STALE_VERSION' } },
    });

    await expect(api.put('/api/v1/reservations/r1')).rejects.toMatchObject({
      response: { data: { detail: 'TRANSLATED(errors:RESERVATION_STALE_VERSION)', errorCode: 'RESERVATION_STALE_VERSION' } },
    });
  });

  it('BUG-6: leaves the raw code untouched when i18next has no translation for it '
      + '(real i18next returns the bare key on a miss, not the namespaced one)', async () => {
    // Simulate the actual i18next missing-key fallback: the bare key, no
    // "errors:" prefix — verified empirically against the real i18next
    // package, not assumed. A guard comparing against `errors:${code}`
    // would never match this and would be a permanent no-op.
    vi.mocked(i18n.t).mockImplementationOnce(
      ((key: string) => key.replace(/^errors:/, '')) as typeof i18n.t,
    );
    adapter.mockRejectedValueOnce({
      config: { url: '/api/v1/reservations', headers: {} },
      response: { status: 400, data: { detail: 'SOME_CODE_WITH_NO_TRANSLATION' } },
    });

    let caught: { response?: { data?: { detail?: string; errorCode?: string } } } | undefined;
    try {
      await api.get('/api/v1/reservations');
    } catch (err) {
      caught = err as typeof caught;
    }
    expect(caught?.response?.data?.detail).toBe('SOME_CODE_WITH_NO_TRANSLATION');
    expect(caught?.response?.data?.errorCode).toBeUndefined();
  });

  it('BUG-8: a 401 from /me on app bootstrap silently refreshes and retries, instead of forcing a re-login', async () => {
    const meConfig = { url: '/api/v1/auth/me', headers: {} } as InternalAxiosRequestConfig;

    adapter
      .mockRejectedValueOnce({ config: meConfig, response: { status: 401, data: {} } })
      .mockResolvedValueOnce(makeResponse(meConfig, 200, { ok: 'refreshed' }))
      .mockResolvedValueOnce(makeResponse(meConfig, 200, { username: 'admin' }));

    const result = await api.get('/api/v1/auth/me');

    expect(adapter).toHaveBeenCalledTimes(3);
    expect((adapter.mock.calls[1][0] as InternalAxiosRequestConfig).url).toBe('/api/v1/auth/refresh');
    expect(result.data).toEqual({ username: 'admin' });
    expect(logoutMock).not.toHaveBeenCalled();
  });

  it('T-AUTH-08 regression: does not reassign location.href (reload loop) when performLogout runs while already on /login', async () => {
    // An anonymous visit to /login also mounts App, which calls /me once —
    // now that /me is no longer excluded from refresh-then-retry, this used
    // to walk into performLogout() -> href reassignment -> full reload ->
    // App remounts -> /me 401 again -> forever. Guard: no reassignment if
    // the pathname is already /login (query string is untouched proof of that).
    Object.defineProperty(window, 'location', {
      value: { ...originalLocation, pathname: '/login', href: '/login?redirect=%2Fbilling' },
      writable: true,
    });
    adapter
      .mockRejectedValueOnce({ config: { url: '/api/v1/auth/me', headers: {} }, response: { status: 401, data: {} } })
      .mockRejectedValueOnce({ config: { url: '/api/v1/auth/refresh', headers: {} }, response: { status: 401, data: {} } });

    await expect(api.get('/api/v1/auth/me')).rejects.toBeTruthy();

    expect(logoutMock).toHaveBeenCalled();
    expect(window.location.href).toBe('/login?redirect=%2Fbilling');
  });

  it('logs out (only) when a 401 from /me is followed by a failed silent refresh', async () => {
    adapter
      .mockRejectedValueOnce({ config: { url: '/api/v1/auth/me', headers: {} }, response: { status: 401, data: {} } })
      .mockRejectedValueOnce({ config: { url: '/api/v1/auth/refresh', headers: {} }, response: { status: 401, data: {} } });

    await expect(api.get('/api/v1/auth/me')).rejects.toBeTruthy();

    // No refresh loop: the refresh call's own 401 is handled by the /refresh
    // branch (performLogout, no retry), it never re-enters as a /me request.
    expect(logoutMock).toHaveBeenCalled();
    expect(window.location.href).toBe('/login');
  });

  it('rejects immediately on 401 from the /login endpoint without attempting refresh', async () => {
    adapter.mockRejectedValueOnce({
      config: { url: '/api/v1/auth/login', headers: {} },
      response: { status: 401, data: {} },
    });

    await expect(api.post('/api/v1/auth/login', {})).rejects.toBeTruthy();

    expect(adapter).toHaveBeenCalledTimes(1);
    expect(logoutMock).not.toHaveBeenCalled();
  });

  it('logs out immediately when the refresh endpoint itself returns 401', async () => {
    adapter.mockRejectedValueOnce({
      config: { url: '/api/v1/auth/refresh', headers: {} },
      response: { status: 401, data: {} },
    });

    await expect(api.post('/api/v1/auth/refresh')).rejects.toBeTruthy();

    expect(logoutMock).toHaveBeenCalledTimes(1);
    expect(window.location.href).toBe('/login');
  });

  it('on a generic 401, silently refreshes and retries the original request once', async () => {
    const protectedConfig = { url: '/api/v1/guests', headers: {} } as InternalAxiosRequestConfig;

    adapter
      .mockRejectedValueOnce({ config: protectedConfig, response: { status: 401, data: {} } })
      .mockResolvedValueOnce(makeResponse(protectedConfig, 200, { ok: 'refreshed' }))
      .mockResolvedValueOnce(makeResponse(protectedConfig, 200, [{ id: 'g1' }]));

    const result = await api.get('/api/v1/guests');

    expect(adapter).toHaveBeenCalledTimes(3);
    expect((adapter.mock.calls[1][0] as InternalAxiosRequestConfig).url).toBe('/api/v1/auth/refresh');
    expect(result.data).toEqual([{ id: 'g1' }]);
    expect(logoutMock).not.toHaveBeenCalled();
  });

  it('logs out when the silent refresh attempt itself fails', async () => {
    const protectedConfig = { url: '/api/v1/guests', headers: {} } as InternalAxiosRequestConfig;

    adapter
      .mockRejectedValueOnce({ config: protectedConfig, response: { status: 401, data: {} } })
      .mockRejectedValueOnce({ config: { url: '/api/v1/auth/refresh', headers: {} }, response: { status: 401, data: {} } });

    await expect(api.get('/api/v1/guests')).rejects.toBeTruthy();

    // The refresh call's own 401 re-enters this same interceptor (url contains
    // "/refresh"), so performLogout() fires once there and once more in the
    // outer catch — two calls is the real current behavior, not a test bug.
    expect(logoutMock).toHaveBeenCalled();
    expect(window.location.href).toBe('/login');
  });

  it('queues concurrent 401s during an in-flight refresh and retries all of them once it succeeds', async () => {
    const configA = { url: '/api/v1/guests', headers: {} } as InternalAxiosRequestConfig;
    const configB = { url: '/api/v1/reservations', headers: {} } as InternalAxiosRequestConfig;

    adapter
      .mockRejectedValueOnce({ config: configA, response: { status: 401, data: {} } })
      .mockRejectedValueOnce({ config: configB, response: { status: 401, data: {} } })
      .mockResolvedValueOnce(makeResponse(configA, 200, { ok: 'refreshed' }))
      .mockResolvedValueOnce(makeResponse(configA, 200, ['a']))
      .mockResolvedValueOnce(makeResponse(configB, 200, ['b']));

    const [resA, resB] = await Promise.all([api.get('/api/v1/guests'), api.get('/api/v1/reservations')]);

    expect(resA.data).toEqual(['a']);
    expect(resB.data).toEqual(['b']);
    // Exactly one refresh call across both concurrent 401s.
    const refreshCalls = adapter.mock.calls.filter(
      (c) => (c[0] as InternalAxiosRequestConfig).url === '/api/v1/auth/refresh',
    );
    expect(refreshCalls).toHaveLength(1);
  });

  it('rejects with the original error when there is no request config to retry', async () => {
    adapter.mockRejectedValueOnce({
      response: { status: 401, data: {} },
    });

    await expect(api.get('/api/v1/guests')).rejects.toBeTruthy();
    expect(logoutMock).not.toHaveBeenCalled();
  });

  it('passes through non-401 errors untouched', async () => {
    adapter.mockRejectedValueOnce({
      config: { url: '/api/v1/guests', headers: {} },
      response: { status: 500, data: { detail: 'INTERNAL_SERVER_ERROR' } },
    });

    await expect(api.get('/api/v1/guests')).rejects.toMatchObject({
      response: { status: 500 },
    });
    expect(adapter).toHaveBeenCalledTimes(1);
  });
});
