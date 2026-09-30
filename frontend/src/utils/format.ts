import { enUS, it } from 'date-fns/locale';
import type { Locale } from 'date-fns';

const MS_PER_DAY = 86_400_000;

/** Shown wherever a value is missing. */
export const EMPTY_PLACEHOLDER = '—';

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

/** Euro amount localized to the active language; missing values render as the placeholder. */
export const formatCurrency = (amount: number | null | undefined, language: string): string =>
  amount == null
    ? EMPTY_PLACEHOLDER
    : new Intl.NumberFormat(language, { style: 'currency', currency: 'EUR' }).format(amount);

/** Locale date (no time); missing values render as the placeholder. */
export const formatDate = (value: string | null | undefined, language: string): string =>
  value ? new Date(value).toLocaleDateString(language) : EMPTY_PLACEHOLDER;

/** Locale date and time; missing values render as the placeholder. */
export const formatDateTime = (value: string | null | undefined, language: string): string =>
  value ? new Date(value).toLocaleString(language) : EMPTY_PLACEHOLDER;
