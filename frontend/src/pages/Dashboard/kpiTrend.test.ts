import { describe, it, expect } from 'vitest';
import { kpiTrend } from './kpiTrend';
import type { DaySheetTrendPoint } from '../../types';

const point = (date: string, guestsInHouse: number): DaySheetTrendPoint => ({
  date, arrivals: 1, departures: 2, guestsInHouse, availableRooms: 3,
});

describe('kpiTrend', () => {
  it('returns no trend without snapshots', () => {
    expect(kpiTrend(undefined, 'guestsInHouse', 5, '2026-10-04')).toEqual({ values: [], delta: null });
    expect(kpiTrend([], 'guestsInHouse', 5, '2026-10-04')).toEqual({ values: [], delta: null });
  });

  it('appends today to the snapshots and diffs against yesterday', () => {
    const result = kpiTrend([point('2026-10-02', 30), point('2026-10-03', 40)], 'guestsInHouse', 42, '2026-10-04');
    expect(result).toEqual({ values: [30, 40, 42], delta: 2 });
  });

  it('gives a negative delta when today is lower', () => {
    expect(kpiTrend([point('2026-10-03', 40)], 'guestsInHouse', 35, '2026-10-04').delta).toBe(-5);
  });

  it('keeps the series but drops the delta when the last snapshot is older than yesterday', () => {
    const result = kpiTrend([point('2026-10-01', 30), point('2026-10-02', 40)], 'guestsInHouse', 42, '2026-10-04');
    expect(result).toEqual({ values: [30, 40, 42], delta: null });
  });

  it('crosses month boundaries when finding yesterday', () => {
    expect(kpiTrend([point('2026-09-30', 10)], 'guestsInHouse', 12, '2026-10-01').delta).toBe(2);
  });

  it.each([
    ['arrivals', 1], ['departures', 2], ['availableRooms', 3],
  ] as const)('reads the %s field', (field, expected) => {
    expect(kpiTrend([point('2026-10-03', 0)], field, 9, '2026-10-04').values).toEqual([expected, 9]);
  });
});
