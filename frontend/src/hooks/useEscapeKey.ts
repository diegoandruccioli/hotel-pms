import { useEffect } from 'react';

/**
 * Closes an open overlay (dialog, drawer) when Escape is pressed.
 * No-op while `active` is false, so callers can pass their open state
 * directly instead of guarding the effect themselves.
 */
export function useEscapeKey(active: boolean, onEscape: () => void): void {
  useEffect(() => {
    if (!active) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onEscape();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [active, onEscape]);
}
