import { MaterialIcon } from '../MaterialIcon';
import { cn } from '../../utils/cn';

interface M3ErrorStateProps {
  title: string;
  message: string;
  retryLabel?: string;
  onRetry?: () => void;
  className?: string;
}

/**
 * Replaces the hand-rolled `bg-error-container` panel duplicated across ~26
 * pages. Callers should resolve `message` with `getErrorMessage()`
 * (src/utils/errorMessage.ts) rather than reading `err.message`/casting the
 * error shape themselves — several pages were doing that before this
 * component existed, silently dropping the backend's translated `detail`.
 */
export const M3ErrorState = ({ title, message, retryLabel, onRetry, className = '' }: M3ErrorStateProps) => (
  <div
    role="alert"
    className={cn('flex items-center gap-3 px-4 py-4 rounded-shape-sm bg-error-container text-on-error-container', className)}
  >
    <MaterialIcon name="error" size={20} className="shrink-0" />
    <div>
      <h3 className="text-sm font-medium font-body">{title}</h3>
      <p className="mt-1 text-sm font-body opacity-80">{message}</p>
      {onRetry && retryLabel && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-2 text-sm font-medium underline hover:no-underline"
        >
          {retryLabel}
        </button>
      )}
    </div>
  </div>
);
