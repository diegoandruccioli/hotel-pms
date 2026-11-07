import type { ReactNode } from 'react';
import { describe, it, expect, vi } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useCheckOutStay } from './useStays';
import { stayService } from '../../services';
import { queryKeys } from '../../lib';

vi.mock('../../services/stayService', () => ({
  stayService: { checkOut: vi.fn() },
}));

describe('useCheckOutStay', () => {
  it('patches the cached list page and the cached single-stay detail without choking on either shape', async () => {
    const updated = { id: 's1', status: 'CHECKED_OUT' };
    vi.mocked(stayService.checkOut).mockResolvedValue(updated as never);
    const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    // Both live under the `stays` prefix, but only the page has a `content` array.
    queryClient.setQueryData(queryKeys.stays.list(0, {}), {
      content: [{ id: 's1', status: 'CHECKED_IN' }, { id: 's2', status: 'CHECKED_IN' }],
    });
    queryClient.setQueryData(queryKeys.stays.detail('s1'), { id: 's1', status: 'CHECKED_IN' });
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );

    const { result } = renderHook(() => useCheckOutStay(), { wrapper });
    await act(async () => { await result.current.mutateAsync('s1'); });

    await waitFor(() => {
      expect(queryClient.getQueryData(queryKeys.stays.detail('s1'))).toEqual(updated);
    });
    expect(queryClient.getQueryData(queryKeys.stays.list(0, {}))).toEqual({
      content: [updated, { id: 's2', status: 'CHECKED_IN' }],
    });
  });
});
