import { useCallback, useContext, useEffect, useId } from 'react';
import { UnsavedChangesContext } from './unsavedChangesContext';

const handleBeforeUnload = (event: BeforeUnloadEvent) => {
  event.preventDefault();
  // Some browsers still need a returnValue to show their own confirmation.
  event.returnValue = '';
};

/**
 * Warns before the user loses unsaved edits (R9): in-app navigation asks through a dialog that
 * names `subject`; closing or reloading the tab uses the browser's own prompt.
 *
 * Pass `dirty` as false while a save is running. Call `allowNavigation()` just before navigating
 * away after a successful save. Outside `UnsavedChangesProvider` the router part is inert.
 */
export function useUnsavedChangesGuard(dirty: boolean, subject: string) {
  const registry = useContext(UnsavedChangesContext);
  const id = useId();

  useEffect(() => {
    if (!registry) return undefined;
    registry.set(id, { dirty, subject });
    return () => registry.remove(id);
  }, [registry, id, dirty, subject]);

  useEffect(() => {
    if (!dirty) return undefined;
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [dirty]);

  const allowNavigation = useCallback(() => registry?.allowNext(), [registry]);

  return { allowNavigation };
}
