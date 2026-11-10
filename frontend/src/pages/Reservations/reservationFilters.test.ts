import { describe, it, expect } from 'vitest';
import { readReservationFilters, reservationFilterParams, reservationFiltersToParams } from './reservationFilters';

describe('reservationFilterParams', () => {
  it.each([
    ['all', {}],
    ['upcoming', { upcomingOnly: true }],
    ['pending', { status: 'PENDING' }],
    ['arrivalsToday', { dateFrom: '2026-10-04', dateTo: '2026-10-04' }],
    ['inHouse', { status: 'CHECKED_IN' }],
    ['cancelled', { status: 'CANCELLED' }],
  ] as const)('maps %s to its search params', (preset, expected) => {
    expect(reservationFilterParams(preset, '2026-10-04')).toEqual(expected);
  });
});

describe('readReservationFilters', () => {
  const read = (query: string, nav: Parameters<typeof readReservationFilters>[1] = null) =>
    readReservationFilters(new URLSearchParams(query), nav);

  it('uses the defaults for an empty query string', () => {
    expect(read('')).toEqual({ preset: 'all', sortField: 'checkInDate', sortDir: 'desc' });
  });

  it('reads valid values from the query string', () => {
    expect(read('preset=inHouse&sort=status&dir=asc')).toEqual({ preset: 'inHouse', sortField: 'status', sortDir: 'asc' });
  });

  it('ignores unknown values', () => {
    expect(read('preset=x&sort=y&dir=z')).toEqual({ preset: 'all', sortField: 'checkInDate', sortDir: 'desc' });
  });

  it('falls back to the navigation state before the defaults, and the URL wins over it', () => {
    const nav = { preset: 'upcoming', sortField: 'checkOutDate', sortDir: 'asc' } as const;
    expect(read('', nav)).toEqual({ preset: 'upcoming', sortField: 'checkOutDate', sortDir: 'asc' });
    expect(read('preset=all', nav).preset).toBe('all');
  });
});

describe('reservationFiltersToParams', () => {
  it('serialises the three filters and nothing else', () => {
    const params = reservationFiltersToParams({ preset: 'pending', sortField: 'status', sortDir: 'asc' });
    expect(params.toString()).toBe('preset=pending&sort=status&dir=asc');
  });
});
