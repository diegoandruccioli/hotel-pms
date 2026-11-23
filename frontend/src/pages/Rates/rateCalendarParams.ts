import { format, isValid, startOfMonth } from 'date-fns';

const MONTH = /^\d{4}-(0[1-9]|1[0-2])$/;

/**
 * Month shown by the rate calendar, from `?month=yyyy-MM`. A missing or malformed value is ignored
 * in silence and falls back to the current month: a hand-edited URL is an approximation of intent,
 * not a mistake. `now` is a parameter so the fallback is testable.
 */
export const readRateCalendarMonth = (params: URLSearchParams, now: Date = new Date()): Date => {
  const value = params.get('month');
  if (value !== null && MONTH.test(value)) {
    const date = new Date(`${value}-01T00:00:00`);
    if (isValid(date)) return date;
  }
  return startOfMonth(now);
};

/** Query string for the shown month (`?month=yyyy-MM`). */
export const rateCalendarMonthToSearch = (month: Date): URLSearchParams =>
  new URLSearchParams({ month: format(month, 'yyyy-MM') });
