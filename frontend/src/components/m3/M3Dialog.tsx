import { useEffect, useId, useRef } from 'react';
import * as FocusTrapModule from 'focus-trap-react';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../MaterialIcon';
import { useEscapeKey } from '../../hooks';
import { cn } from '../../utils';

const FocusTrap = FocusTrapModule.default ?? FocusTrapModule;
// Escape is owned by useEscapeKey: the trap must not deactivate itself on it, or a nested
// dialog dismissed with Escape would leave focus free in the one underneath.
const FOCUS_TRAP_OPTIONS = { escapeDeactivates: false };

interface M3DialogProps {
  /** Controls visibility */
  open: boolean;
  /** Accessible title shown in the dialog header */
  title: string;
  /** Id wired to aria-labelledby; generated per instance so stacked dialogs never share one */
  titleId?: string;
  onClose: () => void;
  children: React.ReactNode;
  /**
   * Optional sticky, non-scrolling section rendered below the body (e.g.
   * Cancel/Save buttons on a form). Layout-neutral on purpose — some callers
   * need `justify-end` (plain actions), others `justify-between` (a
   * destructive action on one side, primary actions on the other), so the
   * caller supplies its own flex wrapper instead of this component forcing
   * one. Omit the prop entirely and the body fills the dialog exactly as
   * before — existing callers are unaffected.
   */
  footer?: React.ReactNode;
}

/**
 * M3-compliant full-screen modal dialog with:
 *  - Scrim (semi-transparent backdrop)
 *  - focus-trap-react for keyboard containment
 *  - role="dialog" + aria-modal + aria-labelledby for screen readers
 *  - Escape key closes the dialog (the topmost one, when dialogs are stacked)
 */
export const M3Dialog = ({
  open,
  title,
  titleId: titleIdProp,
  onClose,
  children,
  footer,
}: M3DialogProps) => {
  const { t } = useTranslation('common');
  const generatedTitleId = useId();
  const titleId = titleIdProp ?? generatedTitleId;
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  // Move focus to close button when dialog opens
  useEffect(() => {
    if (open) {
      closeButtonRef.current?.focus();
    }
  }, [open]);

  // Close on Escape key (only the topmost of stacked dialogs reacts, see useEscapeKey)
  useEscapeKey(open, onClose);

  if (!open) return null;

  return (
    <FocusTrap focusTrapOptions={FOCUS_TRAP_OPTIONS}>
      {/* Portal-like fixed overlay */}
      <div
        className="fixed inset-0 z-50 flex items-center justify-center p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        {/* Scrim */}
        <div
          className="absolute inset-0 bg-scrim/40"
          onClick={onClose}
          aria-hidden="true"
        />

        {/* Dialog surface — M3 elevation-3, rounded-shape-lg */}
        <div
          className={cn(
            'relative w-full max-w-lg max-h-[90dvh] overflow-hidden',
            'flex flex-col',
            'bg-surface-container-high rounded-shape-xl',
            'shadow-elevation-3'
          )}
        >
          {/* Header */}
          <div className="flex items-center justify-between px-6 pt-6 pb-4">
            <h2
              id={titleId}
              className="text-xl font-semibold font-display text-on-surface leading-tight"
            >
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

          {/* Divider */}
          <div className="h-px bg-outline-variant mx-6" />

          {/* Scrollable body */}
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
