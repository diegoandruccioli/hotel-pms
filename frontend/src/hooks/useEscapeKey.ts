import { useEffect, useRef, type RefObject } from 'react';

type EscapeHandlerRef = RefObject<() => void>;

/**
 * Active overlays, oldest first. Escape only reaches the last one, so a dialog opened on top of
 * another closes on its own instead of taking everything underneath with it.
 */
const stack: EscapeHandlerRef[] = [];

const handleKeyDown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') stack.at(-1)?.current();
};

const register = (entry: EscapeHandlerRef) => {
  stack.push(entry);
  if (stack.length === 1) document.addEventListener('keydown', handleKeyDown);
};

const unregister = (entry: EscapeHandlerRef) => {
  const index = stack.indexOf(entry);
  if (index !== -1) stack.splice(index, 1);
  if (stack.length === 0) document.removeEventListener('keydown', handleKeyDown);
};

/**
 * Closes an open overlay (dialog, drawer) when Escape is pressed. Overlays are stacked in the
 * order they became active and only the topmost one reacts. No-op while `active` is false, so
 * callers can pass their open state directly instead of guarding the effect themselves.
 *
 * Order is registration order, i.e. the order the effects run. A nested overlay opened later (by
 * a user action) is therefore on top; one that mounts active in the very same commit as its
 * parent would register first, since child effects run before the parent's.
 *
 * The handler is read through a ref: a new `onEscape` identity on re-render neither re-registers
 * the overlay nor moves it in the stack.
 */
export function useEscapeKey(active: boolean, onEscape: () => void): void {
  const handlerRef = useRef(onEscape);

  useEffect(() => {
    handlerRef.current = onEscape;
  }, [onEscape]);

  useEffect(() => {
    if (!active) return;
    register(handlerRef);
    return () => unregister(handlerRef);
  }, [active]);
}
