import { useCallback } from 'react';
import { MaterialIcon } from '../MaterialIcon';
import { cn } from '../../utils';

interface M3SwitchProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  description?: string;
  icon?: string;
  disabled?: boolean;
  className?: string;
}

/**
 * A `role="switch"` button-row, not a native checkbox — matches the
 * existing hand-rolled toggle in SettingsAccessibility.tsx (high-contrast
 * mode) exactly, track/thumb sizing included, so swapping it in there is a
 * pure refactor with no visual change.
 */
export const M3Switch = ({ checked, onChange, label, description, icon, disabled = false, className = '' }: M3SwitchProps) => {
  const handleClick = useCallback(() => {
    if (!disabled) onChange(!checked);
  }, [checked, disabled, onChange]);

  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={handleClick}
      className={cn(
        'flex items-center justify-between w-full px-4 py-3 rounded-shape-md border border-outline-variant transition-colors',
        'hover:bg-surface-container-highest focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2',
        disabled && 'opacity-38 cursor-not-allowed hover:bg-transparent',
        className,
      )}
    >
      <span className="flex items-center gap-3">
        {icon && <MaterialIcon name={icon} size={20} className="text-on-surface-variant" />}
        <span className="text-left">
          <span className="block text-sm font-medium font-body text-on-surface">{label}</span>
          {description && (
            <span className="block text-xs font-body text-on-surface-variant">{description}</span>
          )}
        </span>
      </span>

      <span
        aria-hidden="true"
        className={cn(
          'relative w-12 h-7 shrink-0 rounded-shape-full border-2 transition-colors',
          checked ? 'bg-primary border-primary' : 'bg-surface-container-highest border-outline',
        )}
      >
        <span
          className={cn(
            'absolute top-0.5 block w-5 h-5 rounded-shape-full shadow-elevation-1 transition-all duration-200',
            checked ? 'translate-x-[22px] bg-on-primary' : 'translate-x-0.5 bg-outline',
          )}
        />
      </span>
    </button>
  );
};
