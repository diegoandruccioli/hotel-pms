import { describe, it, expect, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useBreadcrumbs } from './useBreadcrumbs';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: { ns?: string }) => (opts?.ns ? `${opts.ns}:${key}` : key),
  }),
}));

const at = (path: string) => {
  const entries = [path];
  return ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={entries}>{children}</MemoryRouter>
  );
};

describe('useBreadcrumbs', () => {
  it('translates the trail for the current location, group crumb without a link', () => {
    const { result } = renderHook(() => useBreadcrumbs(), { wrapper: at('/reservations/new') });
    expect(result.current).toEqual([
      { label: 'common:nav_group_front_office' },
      { label: 'common:nav_reservations', to: '/reservations' },
      { label: 'common:new_reservation', to: '/reservations/new' },
    ]);
  });

  it('replaces the last label with the given leaf label', () => {
    const { result } = renderHook(() => useBreadcrumbs('Sala Rossi'), { wrapper: at('/reservations/groups/g1') });
    expect(result.current).toHaveLength(3);
    expect(result.current.at(-1)).toEqual({ label: 'Sala Rossi', to: '/reservations/groups/g1' });
  });

  it('returns an empty trail for the dashboard', () => {
    const { result } = renderHook(() => useBreadcrumbs('ignored'), { wrapper: at('/') });
    expect(result.current).toEqual([]);
  });
});
