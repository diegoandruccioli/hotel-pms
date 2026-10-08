import { useEffect, useId, useRef } from 'react';
import * as FocusTrapModule from 'focus-trap-react';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../MaterialIcon';
import { useEscapeKey } from '../../hooks';
import { cn } from '../../utils';

const FocusTrap = FocusTrapModule.default ?? FocusTrapModule;
// Escape is owned by useEscapeKey: the trap must not deactivate itself on it, or a dialog
// dismissed with Escape on top of the sheet would leave focus free in the sheet underneath.
const FOCUS_TRAP_OPTIONS = { escapeDeactivates: false };

interface M3SideSheetProps {
  /** Controls visibility */
  open: boolean;
  /** Accessible title shown in the sheet header */
  title: string;
  onClose: () => void;
  children: React.ReactNode;
  /** Optional sticky, non-scrolling section below the body; the caller supplies its own flex wrapper */
  footer?: React.ReactNode;
}

/**
 * Modal side sheet anchored to the right edge, for the detail of a single record.
 * Same contract as M3Dialog: scrim, focus trap, role="dialog" + aria-modal, Escape closes
 * only the topmost overlay. Full width on mobile, 400px from `sm` up.
 */
export const M3SideSheet = ({ open, title, onClose, children, footer }: M3SideSheetProps) => {
  const { t } = useTranslation('common');
  const titleId = useId();
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (open) {
      closeButtonRef.current?.focus();
    }
  }, [open]);

  useEscapeKey(open, onClose);

  if (!open) return null;

  return (
    <FocusTrap focusTrapOptions={FOCUS_TRAP_OPTIONS}>
      <div
        className="fixed inset-0 z-50 flex justify-end"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <div className="absolute inset-0 bg-scrim/40" onClick={onClose} aria-hidden="true" />

        <div
          className={cn(
            'relative flex h-full w-full max-w-full flex-col overflow-hidden sm:w-[400px]',
            'bg-surface-container-lowest sm:rounded-l-shape-lg',
            'shadow-elevation-3 animate-slide-in-from-right'
          )}
        >
          <div className="flex items-center justify-between px-6 pt-6 pb-4">
            <h2 id={titleId} className="text-xl font-semibold font-display text-on-surface leading-tight">
              {title}
            </h2>
            <button
              ref={closeButtonRef}
              type="button"
              onClick={onClose}
              className={cn(
                'flex items-center justify-center w-10 h-10',
                'rounded-shape-full text-on-surface-variant',
                'hover:bg-surface-container-highest',
                'focus-visible:outline-hidden focus-visible:ring-2',
                'focus-visible:ring-primary focus-visible:ring-offset-2',
                'transition-colors'
              )}
              aria-label={t('close')}
            >
              <MaterialIcon name="close" size={20} />
            </button>
          </div>

          <div className="h-px bg-outline-variant mx-6" />

          <div className="flex-1 overflow-y-auto px-6 py-5">{children}</div>

          {footer && (
            <>
              <div className="h-px bg-outline-variant mx-6" />
              <div className="px-6 py-4">{footer}</div>
            </>
          )}
        </div>
      </div>
    </FocusTrap>
  );
};
