import { describe, it, expect } from 'vitest';
import { quotationListParamsToSearch, readQuotationListParams } from './quotationListParams';

describe('readQuotationListParams', () => {
  it('falls back to "all" and the first page for an empty query string', () => {
    expect(readQuotationListParams(new URLSearchParams())).toEqual({ status: 'ALL', page: 0 });
  });

  it('reads a one-based page', () => {
    expect(readQuotationListParams(new URLSearchParams('page=3'))).toEqual({ status: 'ALL', page: 2 });
  });

  it.each(['0', '1', '-4', '1.5', 'abc', ''])('ignores the malformed or first page %j in silence', (page) => {
    expect(readQuotationListParams(new URLSearchParams({ page })).page).toBe(0);
  });

  it.each(['DRAFT', 'SENT', 'ACCEPTED', 'DECLINED', 'EXPIRED'])('reads the status %s', (status) => {
    expect(readQuotationListParams(new URLSearchParams({ status })).status).toBe(status);
  });

  it.each(['ALL', 'draft', 'BOGUS', ''])('ignores the unknown status %j in silence', (status) => {
    expect(readQuotationListParams(new URLSearchParams({ status })).status).toBe('ALL');
  });
});

describe('quotationListParamsToSearch', () => {
  it('leaves the default (all, first page) out of the URL', () => {
    expect(quotationListParamsToSearch({ status: 'ALL', page: 0 }).toString()).toBe('');
  });

  it('writes the page one-based', () => {
    expect(quotationListParamsToSearch({ status: 'ALL', page: 2 }).toString()).toBe('page=3');
  });

  it('writes the status and the page together', () => {
    expect(quotationListParamsToSearch({ status: 'EXPIRED', page: 1 }).toString()).toBe('status=EXPIRED&page=2');
  });

  it('round-trips with the reader', () => {
    const state = { status: 'SENT', page: 4 } as const;
    expect(readQuotationListParams(quotationListParamsToSearch(state))).toEqual(state);
  });
});
