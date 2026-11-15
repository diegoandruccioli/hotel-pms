import { describe, it, expect } from 'vitest';
import { quotationListParamsToSearch, readQuotationListParams } from './quotationListParams';

describe('readQuotationListParams', () => {
  it('falls back to the first page for an empty query string', () => {
    expect(readQuotationListParams(new URLSearchParams())).toEqual({ page: 0 });
  });

  it('reads a one-based page', () => {
    expect(readQuotationListParams(new URLSearchParams('page=3'))).toEqual({ page: 2 });
  });

  it.each(['0', '1', '-4', '1.5', 'abc', ''])('ignores the malformed or first page %j in silence', (page) => {
    expect(readQuotationListParams(new URLSearchParams({ page })).page).toBe(0);
  });
});

describe('quotationListParamsToSearch', () => {
  it('leaves the first page out of the URL', () => {
    expect(quotationListParamsToSearch({ page: 0 }).toString()).toBe('');
  });

  it('writes the page one-based', () => {
    expect(quotationListParamsToSearch({ page: 2 }).toString()).toBe('page=3');
  });
});
