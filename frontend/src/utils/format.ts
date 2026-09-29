import { enUS, it } from 'date-fns/locale';
import type { Locale } from 'date-fns';

const MS_PER_DAY = 86_400_000;

/** `yyyy-MM-dd` in the local calendar (what a date input and the backend's LocalDate expect). */
export const toIsoDate = (date: Date): string =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

export const todayIsoDate = (): string => toIsoDate(new Date());

/**
 * Adds whole days to a `yyyy-MM-dd` string. Works on the calendar date itself
 * (UTC arithmetic), so the result never depends on the machine's timezone or DST.
 */
export const addDaysIso = (isoDate: string, days: number): string => {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day) + days * MS_PER_DAY).toISOString().slice(0, 10);
};

/** date-fns locale for the active i18n language (Italian or English fallback). */
export const dateFnsLocale = (language: string): Locale => (language.startsWith('it') ? it : enUS);
