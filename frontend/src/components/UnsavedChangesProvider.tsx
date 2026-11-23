import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react';
import { useBlocker, type BlockerFunction } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { UnsavedChangesContext, type UnsavedChangesRegistry } from '../hooks/unsavedChangesContext';
import { M3ConfirmDialog } from './m3';

interface Guard {
  dirty: boolean;
  subject: string;
}

const LOGIN_PATH = '/login';

interface LeaveDialogProps {
  getSubject: () => string;
  onStay: () => void;
  onLeave: () => void;
}

// Mounted only while a navigation is blocked; the subject is fixed at that moment.
function LeaveDialog({ getSubject, onStay, onLeave }: LeaveDialogProps) {
  const { t } = useTranslation('common');
  const [subject] = useState(getSubject);
  return (
    <M3ConfirmDialog
      title={t('unsaved_title')}
      message={subject}
      cancelLabel={t('unsaved_keep_editing')}
      confirmLabel={t('unsaved_leave')}
      onCancel={onStay}
      onConfirm={onLeave}
    />
  );
}

/**
 * Must sit inside a data router. Forms register through `useUnsavedChangesGuard`; while one of
 * them is dirty, a navigation to another page asks first, naming what would be lost (R9).
 */
export function UnsavedChangesProvider({ children }: { children: ReactNode }) {
  const guards = useRef(new Map<string, Guard>());
  const allowOnce = useRef(false);

  const registry = useMemo<UnsavedChangesRegistry>(
    () => ({
      set: (id, guard) => {
        guards.current.set(id, guard);
      },
      remove: (id) => {
        guards.current.delete(id);
      },
      allowNext: () => {
        allowOnce.current = true;
      },
    }),
    [],
  );

  const shouldBlock = useCallback<BlockerFunction>(({ currentLocation, nextLocation }) => {
    if (allowOnce.current) {
      allowOnce.current = false;
      return false;
    }
    if (nextLocation.pathname === currentLocation.pathname) return false;
    if (nextLocation.pathname === LOGIN_PATH) return false;
    return Array.from(guards.current.values()).some((g) => g.dirty);
  }, []);

  const blocker = useBlocker(shouldBlock);
  const getSubject = useCallback(
    () => Array.from(guards.current.values()).find((g) => g.dirty)?.subject ?? '',
    [],
  );

  return (
    <UnsavedChangesContext.Provider value={registry}>
      {children}
      {blocker.state === 'blocked' && (
        <LeaveDialog getSubject={getSubject} onStay={blocker.reset} onLeave={blocker.proceed} />
      )}
    </UnsavedChangesContext.Provider>
  );
}
