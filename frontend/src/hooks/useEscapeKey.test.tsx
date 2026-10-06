import { describe, it, expect, vi } from 'vitest';
import { fireEvent, renderHook } from '@testing-library/react';
import { useEscapeKey } from './useEscapeKey';

const pressEscape = () => fireEvent.keyDown(document, { key: 'Escape' });

describe('useEscapeKey', () => {
  it('calls the handler on Escape while active', () => {
    const onEscape = vi.fn();
    renderHook(() => useEscapeKey(true, onEscape));

    pressEscape();

    expect(onEscape).toHaveBeenCalledTimes(1);
  });

  it('ignores other keys', () => {
    const onEscape = vi.fn();
    renderHook(() => useEscapeKey(true, onEscape));

    fireEvent.keyDown(document, { key: 'Enter' });

    expect(onEscape).not.toHaveBeenCalled();
  });

  it('does nothing while inactive', () => {
    const onEscape = vi.fn();
    renderHook(() => useEscapeKey(false, onEscape));

    pressEscape();

    expect(onEscape).not.toHaveBeenCalled();
  });

  it('stops listening after unmount', () => {
    const onEscape = vi.fn();
    const { unmount } = renderHook(() => useEscapeKey(true, onEscape));
    unmount();

    pressEscape();

    expect(onEscape).not.toHaveBeenCalled();
  });

  describe('with stacked overlays', () => {
    it('calls only the most recently opened one', () => {
      const parent = vi.fn();
      const child = vi.fn();
      renderHook(() => useEscapeKey(true, parent));
      renderHook(() => useEscapeKey(true, child));

      pressEscape();

      expect(child).toHaveBeenCalledTimes(1);
      expect(parent).not.toHaveBeenCalled();
    });

    it('hands Escape back to the one underneath once the top one is gone', () => {
      const parent = vi.fn();
      const child = vi.fn();
      renderHook(() => useEscapeKey(true, parent));
      const top = renderHook(() => useEscapeKey(true, child));

      top.unmount();
      pressEscape();

      expect(parent).toHaveBeenCalledTimes(1);
      expect(child).not.toHaveBeenCalled();
    });

    it('treats an overlay that becomes active later as the top one', () => {
      const parent = vi.fn();
      const child = vi.fn();
      renderHook(() => useEscapeKey(true, parent));
      const late = renderHook(({ active }) => useEscapeKey(active, child), { initialProps: { active: false } });

      pressEscape();
      expect(parent).toHaveBeenCalledTimes(1);

      late.rerender({ active: true });
      pressEscape();
      expect(child).toHaveBeenCalledTimes(1);
      expect(parent).toHaveBeenCalledTimes(1);
    });

    it('keeps its place in the stack when the handler identity changes on re-render', () => {
      const parent = vi.fn();
      const child = vi.fn();
      const top = renderHook(({ cb }) => useEscapeKey(true, cb), { initialProps: { cb: parent } });
      renderHook(() => useEscapeKey(true, child));

      // Re-rendering the lower overlay with a new callback must not move it above the other one.
      const parentNext = vi.fn();
      top.rerender({ cb: parentNext });
      pressEscape();

      expect(child).toHaveBeenCalledTimes(1);
      expect(parent).not.toHaveBeenCalled();
      expect(parentNext).not.toHaveBeenCalled();
    });

    it('calls the latest handler of the top overlay', () => {
      const first = vi.fn();
      const second = vi.fn();
      const view = renderHook(({ cb }) => useEscapeKey(true, cb), { initialProps: { cb: first } });

      view.rerender({ cb: second });
      pressEscape();

      expect(second).toHaveBeenCalledTimes(1);
      expect(first).not.toHaveBeenCalled();
    });
  });
});
