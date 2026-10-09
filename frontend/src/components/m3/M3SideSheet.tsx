import { cn } from '../../utils';
import { M3ModalShell } from './M3ModalShell';

const OVERLAY_CLASSES = 'fixed inset-0 z-50 flex justify-end';

// Full width on mobile, 400px from `sm` up
const SURFACE_CLASSES = cn(
  'relative flex h-full w-full max-w-full flex-col overflow-hidden sm:w-[400px]',
  'bg-surface-container-lowest sm:rounded-l-shape-lg',
  'shadow-elevation-3 animate-slide-in-from-right'
);

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
 * Same contract as M3Dialog (it shares `M3ModalShell`): scrim, focus trap, role="dialog" +
 * aria-modal, Escape closes only the topmost overlay.
 */
export const M3SideSheet = ({ open, title, onClose, children, footer }: M3SideSheetProps) => (
  <M3ModalShell
    open={open}
    title={title}
    onClose={onClose}
    footer={footer}
    overlayClassName={OVERLAY_CLASSES}
    surfaceClassName={SURFACE_CLASSES}
  >
    {children}
  </M3ModalShell>
);
