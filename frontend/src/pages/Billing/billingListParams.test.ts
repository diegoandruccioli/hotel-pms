import { describe, it, expect } from 'vitest';
import { billingListParamsToSearch, readBillingListParams } from './billingListParams';
import type { BillingListParams } from './billingListParams';

const DEFAULTS: BillingListParams = {
  status: 'ALL', sortField: 'issueDate', sortDir: 'desc', page: 0, dateFrom: '', dateTo: '',
};

describe('readBillingListParams', () => {
  it('falls back to the defaults on an empty query string', () => {
    expect(readBillingListParams(new URLSearchParams(), null)).toEqual(DEFAULTS);
  });

  it('reads every parameter, with the page one-based in the URL', () => {
    const params = new URLSearchParams('status=PAID&sort=status&dir=asc&page=3&from=2026-01-01&to=2026-01-31');
    expect(readBillingListParams(params, null)).toEqual({
      status: 'PAID', sortField: 'status', sortDir: 'asc', page: 2, dateFrom: '2026-01-01', dateTo: '2026-01-31',
    });
  });

  it('ignores unknown or malformed values', () => {
    const params = new URLSearchParams('status=X&sort=name&dir=sideways&page=abc&from=1/1/2026&to=');
    expect(readBillingListParams(params, null)).toEqual(DEFAULTS);
    expect(readBillingListParams(new URLSearchParams('page=1.5'), null).page).toBe(0);
    expect(readBillingListParams(new URLSearchParams('page=1'), null).page).toBe(0);
  });

  it('uses the navigation state only when the URL has no status', () => {
    const nav = { statusFilter: 'ISSUED' as const };
    expect(readBillingListParams(new URLSearchParams(), nav).status).toBe('ISSUED');
    expect(readBillingListParams(new URLSearchParams('status=ALL'), nav).status).toBe('ALL');
    expect(readBillingListParams(new URLSearchParams('status=PAID'), nav).status).toBe('PAID');
  });
});

describe('billingListParamsToSearch', () => {
  it('always writes the status and leaves the other defaults out', () => {
    expect(billingListParamsToSearch(DEFAULTS).toString()).toBe('status=ALL');
  });

  it('round-trips a full state', () => {
    const state: BillingListParams = {
      status: 'ISSUED', sortField: 'totalAmount', sortDir: 'asc', page: 4, dateFrom: '2026-02-01', dateTo: '2026-02-28',
    };
    expect(readBillingListParams(billingListParamsToSearch(state), null)).toEqual(state);
  });
});
