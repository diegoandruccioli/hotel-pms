import { MaterialIcon } from '../MaterialIcon';
import { cn } from '../../utils';

interface M3LoadingStateProps {
  /** Translated, screen-reader-only label (e.g. t('loading_guests')) — required
   * so `role="status"` always has something meaningful to announce instead of
   * relying on the spinning icon alone. */
  label: string;
  /** Drops the card surface (background, radius, shadow) for spinners that sit
   * directly on the page or inside a dialog/section that has its own surface. */
  plain?: boolean;
  className?: string;
}

/**
 * Replaces the `<MaterialIcon name="progress_activity" ... animate-spin />`
 * block that was hand-copied into ~24 pages (`plain` for spinners that sit
 * on the page or in a surface of their own). `role="status"` gives it an
 * implicit `aria-live="polite"`, so a screen reader is told loading started
 * without a separate live region.
 */
export const M3LoadingState = ({ label, plain = false, className = '' }: M3LoadingStateProps) => (
  <div
    role="status"
    className={cn(
      'flex justify-center items-center h-64',
      !plain && 'bg-surface rounded-shape-md shadow-elevation-1',
      className,
    )}
  >
    <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
    <span className="sr-only">{label}</span>
  </div>
);
