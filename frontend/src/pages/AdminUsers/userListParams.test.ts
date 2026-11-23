import { describe, it, expect } from 'vitest';
import { readUserListParams, userListParamsToSearch } from './userListParams';

const DEFAULTS = { filter: 'ALL', sortField: 'username', sortDir: 'asc' } as const;

describe('readUserListParams', () => {
  it('falls back to all users, sorted by username ascending, for an empty query string', () => {
    expect(readUserListParams(new URLSearchParams())).toEqual(DEFAULTS);
  });

  it.each(['ADMIN', 'OWNER', 'RECEPTIONIST', 'DEACTIVATED'])('reads the filter %s', (filter) => {
    expect(readUserListParams(new URLSearchParams({ filter })).filter).toBe(filter);
  });

  it.each(['ALL', 'owner', 'BOGUS', ''])('ignores the unknown filter %j in silence', (filter) => {
    expect(readUserListParams(new URLSearchParams({ filter })).filter).toBe('ALL');
  });

  it.each(['username', 'email', 'role', 'active'])('reads the sort field %s', (sort) => {
    expect(readUserListParams(new URLSearchParams({ sort })).sortField).toBe(sort);
  });

  it.each(['password', 'Email', '', 'id'])('ignores the unknown sort field %j in silence', (sort) => {
    expect(readUserListParams(new URLSearchParams({ sort })).sortField).toBe('username');
  });

  it.each(['asc', 'desc'])('reads the direction %s', (dir) => {
    expect(readUserListParams(new URLSearchParams({ dir })).sortDir).toBe(dir);
  });

  it.each(['up', 'DESC', ''])('ignores the unknown direction %j in silence', (dir) => {
    expect(readUserListParams(new URLSearchParams({ dir })).sortDir).toBe('asc');
  });

  it('never reads a search text from the query string', () => {
    expect(readUserListParams(new URLSearchParams({ q: 'mario', search: 'mario' }))).toEqual(DEFAULTS);
  });
});

describe('userListParamsToSearch', () => {
  it('leaves the defaults out of the URL', () => {
    expect(userListParamsToSearch(DEFAULTS).toString()).toBe('');
  });

  it('writes only what differs from the defaults', () => {
    expect(userListParamsToSearch({ ...DEFAULTS, filter: 'OWNER' }).toString()).toBe('filter=OWNER');
    expect(userListParamsToSearch({ ...DEFAULTS, sortField: 'email', sortDir: 'desc' }).toString())
      .toBe('sort=email&dir=desc');
  });

  it('round-trips with the reader', () => {
    const state = { filter: 'DEACTIVATED', sortField: 'role', sortDir: 'desc' } as const;
    expect(readUserListParams(userListParamsToSearch(state))).toEqual(state);
  });
});
