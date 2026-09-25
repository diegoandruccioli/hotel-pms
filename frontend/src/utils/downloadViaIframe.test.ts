import { describe, it, expect, vi, afterEach } from 'vitest';

const apiGetMock = vi.hoisted(() => vi.fn());
vi.mock('../services/api', () => ({
  default: { get: apiGetMock },
}));

import { downloadViaIframe } from './downloadViaIframe';

describe('downloadViaIframe', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
    document.body.replaceChildren();
  });

  it('pings an authenticated endpoint (triggering token refresh if needed) before appending the iframe', async () => {
    apiGetMock.mockResolvedValueOnce({});

    await downloadViaIframe('/api/v1/invoices/export.csv');

    expect(apiGetMock).toHaveBeenCalledWith('/api/v1/auth/me');
    const iframe = document.body.querySelector('iframe');
    expect(iframe).not.toBeNull();
    expect(iframe?.style.display).toBe('none');
    expect(iframe?.src).toContain('/api/v1/invoices/export.csv');
  });

  it('removes the iframe after the cleanup delay', async () => {
    apiGetMock.mockResolvedValueOnce({});
    vi.useFakeTimers();

    await downloadViaIframe('/api/v1/guests/export.csv');
    expect(document.body.querySelector('iframe')).not.toBeNull();

    vi.runAllTimers();
    expect(document.body.querySelector('iframe')).toBeNull();
  });

  it('respects a custom cleanup delay', async () => {
    apiGetMock.mockResolvedValueOnce({});
    vi.useFakeTimers();

    await downloadViaIframe('/api/v1/quotations/q1/pdf', 30000);
    vi.advanceTimersByTime(10000);
    expect(document.body.querySelector('iframe')).not.toBeNull();

    vi.advanceTimersByTime(20000);
    expect(document.body.querySelector('iframe')).toBeNull();
  });

  it('never appends the iframe when the auth ping rejects (session truly expired)', async () => {
    apiGetMock.mockRejectedValueOnce(new Error('session expired'));

    await expect(downloadViaIframe('/api/v1/invoices/export.csv')).rejects.toThrow('session expired');
    expect(document.body.querySelector('iframe')).toBeNull();
  });
});
