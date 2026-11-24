import { renderHook, act } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { useSnapshotDirty } from './useSnapshotDirty';

describe('useSnapshotDirty', () => {
  it('is never dirty before the form is ready, whatever the snapshot does', () => {
    const { result, rerender } = renderHook(({ snap, ready }) => useSnapshotDirty(snap, ready), {
      initialProps: { snap: 'a', ready: false },
    });
    rerender({ snap: 'b', ready: false });
    expect(result.current.dirty).toBe(false);
  });

  it('takes the first ready snapshot as the baseline (e.g. loaded data), not the empty one', () => {
    const { result, rerender } = renderHook(({ snap, ready }) => useSnapshotDirty(snap, ready), {
      initialProps: { snap: 'empty', ready: false },
    });
    rerender({ snap: 'loaded', ready: true });
    expect(result.current.dirty).toBe(false);
    rerender({ snap: 'edited', ready: true });
    expect(result.current.dirty).toBe(true);
  });

  it('is clean again when the edit is put back', () => {
    const { result, rerender } = renderHook(({ snap }) => useSnapshotDirty(snap, true), {
      initialProps: { snap: 'a' },
    });
    rerender({ snap: 'b' });
    rerender({ snap: 'a' });
    expect(result.current.dirty).toBe(false);
  });

  it('markSaved makes the current snapshot the new baseline', () => {
    const { result, rerender } = renderHook(({ snap }) => useSnapshotDirty(snap, true), {
      initialProps: { snap: 'a' },
    });
    rerender({ snap: 'b' });
    expect(result.current.dirty).toBe(true);
    act(() => result.current.markSaved());
    expect(result.current.dirty).toBe(false);
  });
});
