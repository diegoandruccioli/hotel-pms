import { useId } from 'react';
import { cn } from '../../utils';

interface M3CheckboxProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'id' | 'type'> {
  label: string;
  supportingText?: string;
}

/** Matches the label-beside-checkbox markup already hand-rolled in 4 files
 * (HotelProfile, MenuFormModal, RateBulkApplyDialog, Stays/GuestFieldSection). */
export const M3Checkbox = ({ label, supportingText, className = '', ...rest }: M3CheckboxProps) => {
  const id = useId();

  return (
    <div className={cn('relative flex items-start gap-3 min-h-11 sm:pointer-fine:min-h-0', className)}>
      <input
        id={id}
        type="checkbox"
        className="relative z-10 mt-0.5 h-4 w-4 shrink-0 rounded-sm border-outline text-primary focus:outline-hidden focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-1"
        aria-describedby={supportingText ? `${id}-support` : undefined}
        {...rest}
      />
      <div>
        <label htmlFor={id} className="text-sm font-medium font-body text-on-surface after:absolute after:inset-0">
          {label}
        </label>
        {supportingText && (
          <p id={`${id}-support`} className="text-xs font-body text-on-surface-variant mt-0.5">
            {supportingText}
          </p>
        )}
      </div>
    </div>
  );
};
