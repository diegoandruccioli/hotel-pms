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

/** Signed whole number as text ("+3", "−2", "0"): trend icons are decorative, so the sign must be in the words. */
export const formatSigned = (value: number): string =>
  `${value > 0 ? '+' : value < 0 ? '−' : ''}${Math.abs(value)}`;

const DATE_ONLY = /^\d{4}-\d{2}-\d{2}$/;

/** Locale date (no time); missing values render as the placeholder. A bare `yyyy-MM-dd` is a calendar date, read in local time so it never shows the previous day west of UTC. */
export const formatDate = (value: string | null | undefined, language: string): string => {
  if (!value) return EMPTY_PLACEHOLDER;
  const date = DATE_ONLY.test(value) ? new Date(`${value}T00:00:00`) : new Date(value);
  return date.toLocaleDateString(language);
};

/** Locale date and time; missing values render as the placeholder. */
export const formatDateTime = (value: string | null | undefined, language: string): string =>
  value ? new Date(value).toLocaleString(language) : EMPTY_PLACEHOLDER;

/** Whole nights between two `yyyy-MM-dd` dates (calendar arithmetic, immune to DST); `null` when either is missing or invalid. */
export const nightsBetween = (checkIn: string | null | undefined, checkOut: string | null | undefined): number | null => {
  if (!checkIn || !checkOut) return null;
  const nights = (Date.parse(`${checkOut}T00:00:00Z`) - Date.parse(`${checkIn}T00:00:00Z`)) / MS_PER_DAY;
  return Number.isFinite(nights) ? nights : null;
};
