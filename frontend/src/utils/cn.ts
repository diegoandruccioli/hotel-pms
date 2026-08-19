import { clsx } from 'clsx';
import type { ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Combines `clsx`'s conditional class composition with `tailwind-merge`'s
 * conflict resolution, so a later Tailwind utility always wins over an
 * earlier one that targets the same CSS property (e.g. `px-2` overridden by
 * a caller-supplied `px-4`) instead of the outcome depending on source order
 * in the generated stylesheet.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
