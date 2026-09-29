import { useEffect, useState } from 'react';

const DEFAULT_DEBOUNCE_MS = 300;

/** Returns `value` once it has stopped changing for `delayMs`; starts equal to `value`. */
export function useDebounce<T>(value: T, delayMs: number = DEFAULT_DEBOUNCE_MS): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(id);
  }, [value, delayMs]);

  return debounced;
}
