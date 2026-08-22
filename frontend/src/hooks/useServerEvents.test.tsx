import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useServerEvents } from './useServerEvents';
import { queryKeys } from '../lib/queryKeys';

class MockEventSource {
  static instances: MockEventSource[] = [];
  onmessage: ((event: MessageEvent<string>) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  readonly url: string;
  close = vi.fn();

  constructor(url: string) {
    this.url = url;
    MockEventSource.instances.push(this);
  }

  emit(data: unknown) {
    this.onmessage?.({ data: JSON.stringify(data) } as MessageEvent<string>);
  }

  emitRaw(data: string) {
    this.onmessage?.({ data } as MessageEvent<string>);
  }
}

describe('useServerEvents', () => {
  beforeEach(() => {
    MockEventSource.instances = [];
    vi.stubGlobal('EventSource', MockEventSource);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const renderWithClient = () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
    const result = renderHook(() => useServerEvents(), { wrapper });
    return { ...result, invalidateSpy };
  };

  it('opens an EventSource pointed at the events stream endpoint', () => {
    renderWithClient();

    expect(MockEventSource.instances).toHaveLength(1);
    expect(MockEventSource.instances[0].url).toBe('/api/v1/events/stream');
  });

  it('invalidates only the rooms cache on ROOM_STATUS_CHANGED', () => {
    const { invalidateSpy } = renderWithClient();
    const source = MockEventSource.instances[0];

    source.emit({ type: 'ROOM_STATUS_CHANGED', timestamp: '2026-01-01T00:00:00Z' });

    expect(invalidateSpy).toHaveBeenCalledExactlyOnceWith({ queryKey: queryKeys.rooms.all });
  });

  it('invalidates rooms, reservations, and stays on CHECK_IN', () => {
    const { invalidateSpy } = renderWithClient();
    const source = MockEventSource.instances[0];

    source.emit({ type: 'CHECK_IN', timestamp: '2026-01-01T00:00:00Z' });

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.rooms.all });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.reservations.all });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: queryKeys.stays.all });
    expect(invalidateSpy).toHaveBeenCalledTimes(3);
  });

  it('invalidates rooms, reservations, and stays on CHECK_OUT', () => {
    const { invalidateSpy } = renderWithClient();
    const source = MockEventSource.instances[0];

    source.emit({ type: 'CHECK_OUT', timestamp: '2026-01-01T00:00:00Z' });

    expect(invalidateSpy).toHaveBeenCalledTimes(3);
  });

  it('ignores malformed JSON payloads', () => {
    const { invalidateSpy } = renderWithClient();
    const source = MockEventSource.instances[0];

    source.emitRaw('not json');

    expect(invalidateSpy).not.toHaveBeenCalled();
  });

  it('ignores a payload with an unknown event type', () => {
    const { invalidateSpy } = renderWithClient();
    const source = MockEventSource.instances[0];

    source.emit({ type: 'SOMETHING_ELSE', timestamp: '2026-01-01T00:00:00Z' });

    expect(invalidateSpy).not.toHaveBeenCalled();
  });

  it('closes the connection on unmount', () => {
    const { unmount } = renderWithClient();
    const source = MockEventSource.instances[0];

    unmount();

    expect(source.close).toHaveBeenCalledOnce();
  });
});
