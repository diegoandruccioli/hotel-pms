import { describe, it, expect } from 'vitest';
import { rateCalendarMonthToSearch, readRateCalendarMonth } from './rateCalendarParams';

const NOW = new Date(2026, 10, 22, 15, 30); // 22 Nov 2026

describe('readRateCalendarMonth', () => {
  it('falls back to the first day of the current month for an empty query string', () => {
    expect(readRateCalendarMonth(new URLSearchParams(), NOW)).toEqual(new Date(2026, 10, 1));
  });

  it('reads a valid month', () => {
    expect(readRateCalendarMonth(new URLSearchParams({ month: '2027-02' }), NOW)).toEqual(new Date(2027, 1, 1));
  });

  it.each(['2026-13', '2026-00', '2026-1', 'abc', '2026-02-01', '', ' 2026-02'])(
    'ignores the malformed month %j in silence',
    (month) => {
      expect(readRateCalendarMonth(new URLSearchParams({ month }), NOW)).toEqual(new Date(2026, 10, 1));
    },
  );
});

describe('rateCalendarMonthToSearch', () => {
  it('writes the month as yyyy-MM', () => {
    expect(rateCalendarMonthToSearch(new Date(2027, 1, 1)).toString()).toBe('month=2027-02');
  });

  it('round-trips with the reader', () => {
    const month = new Date(2026, 11, 1);
    expect(readRateCalendarMonth(rateCalendarMonthToSearch(month), NOW)).toEqual(month);
  });
});
