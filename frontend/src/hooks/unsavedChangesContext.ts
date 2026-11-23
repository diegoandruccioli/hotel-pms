import { createContext } from 'react';

export interface UnsavedChangesRegistry {
  /** Adds or updates the guard of one mounted form. */
  set: (id: string, guard: { dirty: boolean; subject: string }) => void;
  remove: (id: string) => void;
  /** Lets the next navigation through (used right after a successful save). */
  allowNext: () => void;
}

/** Null outside the provider: guards are then inert (unit tests rendered in a plain router). */
export const UnsavedChangesContext = createContext<UnsavedChangesRegistry | null>(null);
