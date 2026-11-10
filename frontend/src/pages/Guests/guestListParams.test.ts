import { describe, it, expect } from 'vitest';
import { guestListParamsToSearch, readGuestListParams } from './guestListParams';

describe('readGuestListParams', () => {
  it('falls back to the first page, ascending, for an empty query string', () => {
    expect(readGuestListParams(new URLSearchParams())).toEqual({ page: 0, dir: 'asc' });
  });

  it('reads a one-based page and the direction', () => {
    expect(readGuestListParams(new URLSearchParams('page=3&dir=desc'))).toEqual({ page: 2, dir: 'desc' });
  });

  it.each(['0', '-4', '1.5', 'abc', ''])('ignores the malformed page %j in silence', (page) => {
    expect(readGuestListParams(new URLSearchParams({ page })).page).toBe(0);
  });

  it('ignores an unknown direction', () => {
    expect(readGuestListParams(new URLSearchParams('dir=sideways')).dir).toBe('asc');
  });
});

describe('guestListParamsToSearch', () => {
  it('leaves the defaults out of the URL', () => {
    expect(guestListParamsToSearch({ page: 0, dir: 'asc' }).toString()).toBe('');
  });

  it('writes the page one-based and a non-default direction', () => {
    expect(guestListParamsToSearch({ page: 2, dir: 'desc' }).toString()).toBe('dir=desc&page=3');
  });

  it('round-trips with the reader', () => {
    const state = { page: 4, dir: 'desc' as const };
    expect(readGuestListParams(guestListParamsToSearch(state))).toEqual(state);
  });
});
