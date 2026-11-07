import { format, parseISO, subDays } from 'date-fns';
import type { DaySheetTrendPoint } from '../../types';

export type TrendField = 'arrivals' | 'departures' | 'guestsInHouse' | 'availableRooms';

export interface KpiTrend {
  /** Oldest to newest, ending with today's live value; empty without snapshots. */
  values: number[];
  /** Today minus yesterday's snapshot; `null` when yesterday has no snapshot. */
  delta: number | null;
}

const NO_TREND: KpiTrend = { values: [], delta: null };

/**
 * Builds the sparkline series and the "vs yesterday" delta for one KPI from the
 * night-audit snapshots. Snapshots are taken at 03:30 the next morning and count
 * expected arrivals including no-shows, so the delta is approximate. Dates with no
 * completed audit are absent: the delta needs yesterday's point, the series needs
 * at least one snapshot (the card draws a sparkline from two points).
 */
export const kpiTrend = (
  points: readonly DaySheetTrendPoint[] | undefined,
  field: TrendField,
  todayValue: number,
  today: string,
): KpiTrend => {
  if (!points || points.length === 0) return NO_TREND;
  const yesterday = format(subDays(parseISO(today), 1), 'yyyy-MM-dd');
  const last = points[points.length - 1];
  return {
    values: [...points.map((p) => p[field]), todayValue],
    delta: last.date === yesterday ? todayValue - last[field] : null,
  };
};
