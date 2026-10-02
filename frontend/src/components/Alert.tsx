import type { ReactNode } from 'react';
import { cn } from '../utils';
import type { StatusTone } from '../utils/domainStatus';
import { toneChipClasses } from '../utils/toneStyles';
import { MaterialIcon } from './MaterialIcon';

type AlertTone = Exclude<StatusTone, 'neutral'>;

interface AlertProps {
  tone: AlertTone;
  /** Material Symbols name; defaults to the tone's icon. */
  icon?: string;
  /** Bold first line above `children`. */
  title?: string;
  /** Trailing action (a link or button), kept clear of the text. */
  action?: ReactNode;
  /** Smaller padding and text, for banners squeezed inside a section. */
  compact?: boolean;
  /** `alert` interrupts the screen reader and is the default for errors;
   * `status` is polite and the default for everything else. */
  role?: 'alert' | 'status';
  className?: string;
  children: ReactNode;
}

const toneIcons: Record<AlertTone, string> = {
  error: 'error',
  warning: 'warning',
  success: 'check_circle',
  info: 'info',
};

/** Inline banner for a message that belongs to the page (a failed save, a
 * configuration warning, a pre-check). Replaces the ~20 hand-written
 * `bg-*-container` blocks; tone colors come from `toneChipClasses`, the same
 * source as status chips. */
export const Alert = ({
  tone,
  icon,
  title,
  action,
  compact = false,
  role,
  className,
  children,
}: AlertProps) => (
  <div
    role={role ?? (tone === 'error' ? 'alert' : 'status')}
    className={cn(
      'flex items-start rounded-shape-sm font-body',
      compact ? 'gap-2 px-3 py-2 text-xs' : 'gap-3 px-4 py-3 text-sm',
      toneChipClasses[tone],
      className,
    )}
  >
    <MaterialIcon name={icon ?? toneIcons[tone]} size={compact ? 16 : 20} className="shrink-0" />
    <div className="min-w-0 flex-1">
      {title && <p className="font-medium">{title}</p>}
      {children}
    </div>
    {action}
  </div>
);
