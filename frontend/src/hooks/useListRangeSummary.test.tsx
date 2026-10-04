import { describe, it, expect, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useListRangeSummary } from './useListRangeSummary';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, o: { from: string; to: string; total: string }) => `${key} ${o.from}-${o.to}/${o.total}`,
    i18n: { language: 'en' },
  }),
}));

describe('useListRangeSummary', () => {
  it('describes the first page', () => {
    const { result } = renderHook(() => useListRangeSummary(0, 20, 20, 214));
    expect(result.current).toBe('list_range_summary 1-20/214');
  });

  it('offsets by the page and counts a short last page', () => {
    const { result } = renderHook(() => useListRangeSummary(2, 20, 7, 47));
    expect(result.current).toBe('list_range_summary 41-47/47');
  });

  it('formats large numbers for the language', () => {
    const { result } = renderHook(() => useListRangeSummary(0, 20, 20, 12345));
    expect(result.current).toBe('list_range_summary 1-20/12,345');
  });

  it.each([[0, 0], [5, 0], [0, 5]])('is undefined for total %s and shown %s', (total, shown) => {
    const { result } = renderHook(() => useListRangeSummary(0, 20, shown, total));
    expect(result.current).toBeUndefined();
  });
});
