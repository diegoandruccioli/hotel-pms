import { cn } from '../../utils';
import { M3ModalShell } from './M3ModalShell';

const OVERLAY_CLASSES = 'fixed inset-0 z-50 flex items-center justify-center p-4';

// M3 elevation-3, rounded-shape-xl
const SURFACE_CLASSES = cn(
  'relative w-full max-w-lg max-h-[90dvh] overflow-hidden',
  'flex flex-col',
  'bg-surface-container-high rounded-shape-xl',
  'shadow-elevation-3'
);

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
 * M3-compliant centred modal dialog. Scrim, focus trap, role="dialog" + aria-modal, focus on
 * the close button and Escape closing the topmost overlay come from `M3ModalShell`.
 */
export const M3Dialog = ({ open, title, titleId, onClose, children, footer }: M3DialogProps) => (
  <M3ModalShell
    open={open}
    title={title}
    titleId={titleId}
    onClose={onClose}
    footer={footer}
    overlayClassName={OVERLAY_CLASSES}
    surfaceClassName={SURFACE_CLASSES}
  >
    {children}
  </M3ModalShell>
);
