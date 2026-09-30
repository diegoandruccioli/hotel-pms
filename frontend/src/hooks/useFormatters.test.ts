import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useFormatters } from './useFormatters';

let language = 'en';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ i18n: { language } }),
}));

const nbsp = (value: string) => value.replace(/\u00A0/g, ' ');

describe('useFormatters', () => {
  beforeEach(() => {
    language = 'en';
  });

  it('formats currency in the active language', () => {
    const { result } = renderHook(() => useFormatters());
    expect(result.current.formatCurrency(1234.5)).toBe('€1,234.50');
  });

  it('keeps the same identity while the language is unchanged', () => {
    const { result, rerender } = renderHook(() => useFormatters());
    const first = result.current;
    rerender();
    expect(result.current).toBe(first);
  });

  it('changes when the language changes', () => {
    const { result, rerender } = renderHook(() => useFormatters());
    const first = result.current;
    language = 'it';
    rerender();
    expect(result.current).not.toBe(first);
    expect(nbsp(result.current.formatCurrency(12345.5))).toBe('12.345,50 €');
  });
});
