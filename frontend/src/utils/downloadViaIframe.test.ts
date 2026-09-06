import { describe, it, expect, vi, afterEach } from 'vitest';
import { downloadViaIframe } from './downloadViaIframe';

describe('downloadViaIframe', () => {
  afterEach(() => {
    vi.useRealTimers();
    document.body.replaceChildren();
  });

  it('appends a hidden iframe pointed at the given URL', () => {
    downloadViaIframe('/api/v1/invoices/export.csv');

    const iframe = document.body.querySelector('iframe');
    expect(iframe).not.toBeNull();
    expect(iframe?.style.display).toBe('none');
    expect(iframe?.src).toContain('/api/v1/invoices/export.csv');
  });

  it('removes the iframe after the cleanup delay', () => {
    vi.useFakeTimers();

    downloadViaIframe('/api/v1/guests/export.csv');
    expect(document.body.querySelector('iframe')).not.toBeNull();

    vi.runAllTimers();
    expect(document.body.querySelector('iframe')).toBeNull();
  });
});
