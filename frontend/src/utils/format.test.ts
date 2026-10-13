import { describe, it, expect, vi, afterEach } from 'vitest';
import { it as itLocale, enUS } from 'date-fns/locale';
import { addDaysIso, dateFnsLocale, EMPTY_PLACEHOLDER, formatCurrency, formatDate, formatDateTime, formatSigned, nightsBetween, todayIsoDate, toIsoDate } from './format';

describe('toIsoDate', () => {
  it('formats the local calendar date with zero padding', () => {
    expect(toIsoDate(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05');
  });
});

describe('todayIsoDate', () => {
  afterEach(() => vi.useRealTimers());

  it('uses the local date, not the UTC one', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 7, 20, 0, 30));
    expect(todayIsoDate()).toBe('2026-08-20');
  });
});

describe('addDaysIso', () => {
  it.each([
    ['2026-08-20', 1, '2026-08-21'],
    ['2026-08-31', 1, '2026-09-01'],
    ['2026-12-31', 1, '2027-01-01'],
    ['2028-02-28', 1, '2028-02-29'],
    ['2026-03-28', 2, '2026-03-30'],
    ['2026-10-24', 2, '2026-10-26'],
    ['2026-03-01', -1, '2026-02-28'],
    ['2026-08-20', 0, '2026-08-20'],
  ])('%s + %i day(s) = %s', (start, days, expected) => {
    expect(addDaysIso(start, days)).toBe(expected);
  });
});

describe('dateFnsLocale', () => {
  it('returns Italian for it and it-IT', () => {
    expect(dateFnsLocale('it')).toBe(itLocale);
    expect(dateFnsLocale('it-IT')).toBe(itLocale);
  });

  it('falls back to English', () => {
    expect(dateFnsLocale('en')).toBe(enUS);
    expect(dateFnsLocale('fr')).toBe(enUS);
  });
});

describe('formatCurrency', () => {
  const nbsp = (value: string) => value.replace(/\u00A0/g, ' ');

  it('formats euros in English and Italian', () => {
    expect(formatCurrency(1234.5, 'en')).toBe('€1,234.50');
    expect(nbsp(formatCurrency(12345.5, 'it'))).toBe('12.345,50 €');
  });

  it('groups four-digit amounts in Italian too, so side-by-side figures read alike', () => {
    expect(nbsp(formatCurrency(2310, 'it'))).toBe('2.310,00 €');
    expect(nbsp(formatCurrency(12480, 'it'))).toBe('12.480,00 €');
    expect(formatCurrency(2310, 'en')).toBe('€2,310.00');
  });

  it('formats zero and negative amounts', () => {
    expect(formatCurrency(0, 'en')).toBe('€0.00');
    expect(formatCurrency(-5, 'en')).toBe('-€5.00');
  });

  it('renders the placeholder for null and undefined', () => {
    expect(formatCurrency(null, 'en')).toBe(EMPTY_PLACEHOLDER);
    expect(formatCurrency(undefined, 'it')).toBe(EMPTY_PLACEHOLDER);
  });
});

describe('formatDate / formatDateTime', () => {
  it('keeps a date-only value on its own day in a timezone west of UTC', () => {
    const previous = process.env.TZ;
    process.env.TZ = 'America/New_York';
    try {
      expect(formatDate('2026-04-01', 'en')).toBe('4/1/2026');
    } finally {
      if (previous === undefined) delete process.env.TZ; else process.env.TZ = previous;
    }
  });

  it('formats a date without time', () => {
    expect(formatDate('2026-08-20T10:30:00', 'en')).toBe('8/20/2026');
    expect(formatDate('2026-08-20T10:30:00', 'it')).toBe('20/08/2026');
  });

  it('formats date and time', () => {
    expect(formatDateTime('2026-08-20T10:30:00', 'en')).toBe(new Date('2026-08-20T10:30:00').toLocaleString('en'));
    expect(formatDateTime('2026-08-20T10:30:00', 'en')).toContain('10:30');
  });

  it.each([undefined, null, ''])('renders the placeholder for %s', (value) => {
    expect(formatDate(value, 'en')).toBe(EMPTY_PLACEHOLDER);
    expect(formatDateTime(value, 'en')).toBe(EMPTY_PLACEHOLDER);
  });
});

describe('formatSigned', () => {
  it.each([[3, '+3'], [-2, '−2'], [0, '0']])('formats %s as %s', (value, expected) => {
    expect(formatSigned(value)).toBe(expected);
  });
});

describe('nightsBetween', () => {
  it('counts calendar nights, also across a DST change', () => {
    expect(nightsBetween('2026-04-01', '2026-04-03')).toBe(2);
    expect(nightsBetween('2026-03-28', '2026-03-30')).toBe(2);
  });

  it.each([[undefined, '2026-04-03'], ['2026-04-01', null], ['', ''], ['bad', '2026-04-03']])(
    'returns null for %s / %s',
    (checkIn, checkOut) => {
      expect(nightsBetween(checkIn as string | undefined, checkOut as string | null)).toBeNull();
    },
  );
});
