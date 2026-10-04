import { describe, it, expect } from 'vitest';
import { reservationFilterParams } from './reservationFilters';

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
