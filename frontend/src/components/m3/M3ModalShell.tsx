import { useEffect, useId, useRef } from 'react';
import * as FocusTrapModule from 'focus-trap-react';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../MaterialIcon';
import { useEscapeKey } from '../../hooks';
import { cn } from '../../utils';

const FocusTrap = FocusTrapModule.default ?? FocusTrapModule;

/**
 * Where focus goes when the overlay closes (runs once the overlay is already out of the DOM).
 * The opener when it is still there; otherwise nowhere if something already took focus (a page
 * restoring it by hand) or another overlay is still open (its trap owns focus); otherwise the
 * main landmark, so Tab continues from the content instead of restarting at the skip link.
 */
function setReturnFocus(opener: HTMLElement | SVGElement): HTMLElement | false {
  if (opener.isConnected && 'focus' in opener) return opener as HTMLElement;
  if (document.activeElement && document.activeElement !== document.body) return false;
  if (document.querySelector('[role="dialog"][aria-modal="true"]')) return false;
  return document.getElementById('main-content') ?? false;
}
// Escape is owned by useEscapeKey: the trap must not deactivate itself on it, or an overlay
// dismissed with Escape on top of another would leave focus free in the one underneath.
const FOCUS_TRAP_OPTIONS = { escapeDeactivates: false, setReturnFocus };

export interface M3ModalShellProps {
  /** Controls visibility */
  open: boolean;
  /** Accessible title shown in the header */
  title: string;
  /** Id wired to aria-labelledby; generated per instance so stacked overlays never share one */
  titleId?: string;
  onClose: () => void;
  children: React.ReactNode;
  /**
   * Optional sticky, non-scrolling section below the body. Layout-neutral on purpose: the
   * caller supplies its own flex wrapper (`justify-end` for plain actions, `justify-between`
   * when a destructive action sits on the other side).
   */
  footer?: React.ReactNode;
  /** Classes of the fixed overlay that positions the surface (centred dialog, right-hand sheet…) */
  overlayClassName: string;
  /** Classes of the surface itself: size, background, shape, elevation, entry animation */
  surfaceClassName: string;
}

/**
 * Behaviour and chrome shared by every modal overlay (`M3Dialog`, `M3SideSheet`): scrim, focus
 * trap, role="dialog" + aria-modal + aria-labelledby, focus on the close button when it opens,
 * Escape closing only the topmost overlay, focus returning to the opener on close (main landmark
 * when the opener is gone) and back on the close button if swapped content drops it, header, scrolling body and optional footer.
 * Internal building block: pages use `M3Dialog` or `M3SideSheet`, which only decide where the
 * surface sits and how it looks.
 */
export const M3ModalShell = ({
  open,
  title,
  titleId: titleIdProp,
  onClose,
  children,
  footer,
  overlayClassName,
  surfaceClassName,
}: M3ModalShellProps) => {
  const { t } = useTranslation('common');
  const generatedTitleId = useId();
  const titleId = titleIdProp ?? generatedTitleId;
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const surfaceRef = useRef<HTMLDivElement>(null);

  // Move focus to the close button when the overlay opens
  useEffect(() => {
    if (open) {
      closeButtonRef.current?.focus();
    }
  }, [open]);

  // Swapping the content (list -> form) can remove the focused element together with an ancestor,
  // which focus-trap does not notice: focus falls to <body> with the overlay still open.
  useEffect(() => {
    const surface = surfaceRef.current;
    if (!open || !surface) return;
    const observer = new MutationObserver(() => {
      if (document.activeElement === document.body) closeButtonRef.current?.focus();
    });
    observer.observe(surface, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, [open]);

  // Close on Escape (only the topmost of stacked overlays reacts, see useEscapeKey)
  useEscapeKey(open, onClose);

  if (!open) return null;

  return (
    <FocusTrap focusTrapOptions={FOCUS_TRAP_OPTIONS}>
      <div className={overlayClassName} role="dialog" aria-modal="true" aria-labelledby={titleId}>
        {/* Scrim */}
        <div className="absolute inset-0 bg-scrim/40" onClick={onClose} aria-hidden="true" />

        <div ref={surfaceRef} className={surfaceClassName}>
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
                'flex items-center justify-center size-11 sm:pointer-fine:size-10',
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
