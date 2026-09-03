import { MaterialIcon } from '../MaterialIcon';
import { cn } from '../../utils';

interface M3EmptyStateProps {
  icon: string;
  title: string;
  description?: string;
  action?: React.ReactNode;
  className?: string;
}

/**
 * Standalone empty state for non-table contexts (grids, cards, dashboards).
 * For a table body, use `M3TableEmptyRow` instead so the message stays
 * inside a valid `<tr><td>` — the DOM this needs to sit in is different, the
 * intent (nothing to show) is the same.
 */
export const M3EmptyState = ({ icon, title, description, action, className = '' }: M3EmptyStateProps) => (
  <div className={cn('flex flex-col items-center justify-center gap-2 py-12 px-6 text-center', className)}>
    <MaterialIcon name={icon} size={32} className="text-on-surface-variant mb-1" />
    <p className="text-sm font-medium font-body text-on-surface">{title}</p>
    {description && (
      <p className="text-sm font-body text-on-surface-variant max-w-sm">{description}</p>
    )}
    {action && <div className="mt-2">{action}</div>}
  </div>
);

interface M3TableEmptyRowProps {
  /** Pass the same `headers.length` used to build the table's header row, so
   * the empty message spans the full width instead of a colSpan hardcoded
   * separately from — and easy to leave stale relative to — the header count. */
  colSpan: number;
  message: string;
}

export const M3TableEmptyRow = ({ colSpan, message }: M3TableEmptyRowProps) => (
  <tr>
    <td colSpan={colSpan} className="py-8 text-center text-sm font-body text-on-surface-variant">
      {message}
    </td>
  </tr>
);
