import { useEffect, useRef } from 'react';
import * as FocusTrapModule from 'focus-trap-react';
import { MaterialIcon } from '../MaterialIcon';

const FocusTrap = FocusTrapModule.default ?? FocusTrapModule;

interface M3DialogProps {
  /** Controls visibility */
  open: boolean;
  /** Accessible title shown in the dialog header */
  title: string;
  /** Unique id wired to aria-labelledby */
  titleId?: string;
  onClose: () => void;
  children: React.ReactNode;
}

/**
 * M3-compliant full-screen modal dialog with:
 *  - Scrim (semi-transparent backdrop)
 *  - focus-trap-react for keyboard containment
 *  - role="dialog" + aria-modal + aria-labelledby for screen readers
 *  - Escape key closes the dialog
 */
export const M3Dialog = ({
  open,
  title,
  titleId = 'dialog-title',
  onClose,
  children,
}: M3DialogProps) => {
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  // Move focus to close button when dialog opens
  useEffect(() => {
    if (open) {
      closeButtonRef.current?.focus();
    }
  }, [open]);

  // Close on Escape key
  useEffect(() => {
    if (!open) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <FocusTrap>
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
          className={[
            'relative w-full max-w-lg max-h-[90dvh] overflow-hidden',
            'flex flex-col',
            'bg-surface-container-high rounded-[28px]',
            'shadow-elevation-3',
          ].join(' ')}
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
              className={[
                'flex items-center justify-center w-10 h-10',
                'rounded-shape-full text-on-surface-variant',
                'hover:bg-surface-container-highest',
                'focus-visible:outline-none focus-visible:ring-2',
                'focus-visible:ring-primary focus-visible:ring-offset-2',
                'transition-colors',
              ].join(' ')}
              aria-label="Chiudi"
            >
              <MaterialIcon name="close" size={20} />
            </button>
          </div>

          {/* Divider */}
          <div className="h-px bg-outline-variant mx-6" />

          {/* Scrollable body */}
          <div className="flex-1 overflow-y-auto px-6 py-5">{children}</div>
        </div>
      </div>
    </FocusTrap>
  );
};
