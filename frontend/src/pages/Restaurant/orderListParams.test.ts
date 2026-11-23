import { describe, it, expect } from 'vitest';
import { orderListParamsToSearch, readOrderListParams } from './orderListParams';

const DEFAULTS = { status: 'ALL', sortField: 'orderDate', sortDir: 'desc' } as const;

describe('readOrderListParams', () => {
  it('falls back to all orders, newest first, for an empty query string', () => {
    expect(readOrderListParams(new URLSearchParams())).toEqual(DEFAULTS);
  });

  it.each(['OPEN', 'CLOSED', 'CANCELLED'])('reads the status %s', (status) => {
    expect(readOrderListParams(new URLSearchParams({ status })).status).toBe(status);
  });

  it.each(['ALL', 'open', 'PENDING', 'BOGUS', ''])('ignores the unknown status %j in silence', (status) => {
    expect(readOrderListParams(new URLSearchParams({ status })).status).toBe('ALL');
  });

  it.each(['orderDate', 'roomNumber', 'guestDisplayName'])('reads the sort field %s', (sort) => {
    expect(readOrderListParams(new URLSearchParams({ sort })).sortField).toBe(sort);
  });

  it.each(['totalAmount', 'id', ''])('ignores the unknown sort field %j in silence', (sort) => {
    expect(readOrderListParams(new URLSearchParams({ sort })).sortField).toBe('orderDate');
  });

  it.each(['asc', 'desc'])('reads the direction %s', (dir) => {
    expect(readOrderListParams(new URLSearchParams({ dir })).sortDir).toBe(dir);
  });

  it.each(['up', 'ASC', ''])('ignores the unknown direction %j in silence', (dir) => {
    expect(readOrderListParams(new URLSearchParams({ dir })).sortDir).toBe('desc');
  });
});

describe('orderListParamsToSearch', () => {
  it('leaves the defaults out of the URL', () => {
    expect(orderListParamsToSearch(DEFAULTS).toString()).toBe('');
  });

  it('writes only what differs from the defaults', () => {
    expect(orderListParamsToSearch({ ...DEFAULTS, status: 'CLOSED' }).toString()).toBe('status=CLOSED');
    expect(orderListParamsToSearch({ ...DEFAULTS, sortField: 'roomNumber', sortDir: 'asc' }).toString())
      .toBe('sort=roomNumber&dir=asc');
  });

  it('round-trips with the reader', () => {
    const state = { status: 'OPEN', sortField: 'guestDisplayName', sortDir: 'asc' } as const;
    expect(readOrderListParams(orderListParamsToSearch(state))).toEqual(state);
  });
});
