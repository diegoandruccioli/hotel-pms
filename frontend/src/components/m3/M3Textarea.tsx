import { useId } from 'react';
import { cn } from '../../utils/cn';

interface M3TextareaProps extends Omit<React.TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'> {
  label: string;
  errorText?: string;
  supportingText?: string;
}

/** Static-label-above textarea, matching the convention M3Select follows —
 * a floating label reads awkwardly once the field grows past one line. */
export const M3Textarea = ({
  label,
  errorText,
  supportingText,
  className = '',
  required,
  rows = 3,
  ...rest
}: M3TextareaProps) => {
  const id = useId();
  const hasError = !!errorText;

  return (
    <div className={className}>
      <label htmlFor={id} className="block text-sm font-medium font-body text-on-surface-variant mb-1">
        {label}
        {required && ' *'}
      </label>
      <textarea
        id={id}
        rows={rows}
        required={required}
        aria-invalid={hasError}
        aria-describedby={errorText ? `${id}-error` : supportingText ? `${id}-support` : undefined}
        className={cn(
          'w-full px-4 py-2.5 rounded-shape-xs border bg-transparent text-sm font-body text-on-surface transition-all focus:outline-hidden',
          hasError
            ? 'border-error ring-2 ring-error ring-offset-1'
            : 'border-outline hover:border-on-surface focus:border-primary focus:ring-2 focus:ring-primary focus:ring-offset-1',
        )}
        {...rest}
      />
      {(errorText || supportingText) && (
        <p
          id={errorText ? `${id}-error` : `${id}-support`}
          className={`mt-1 text-sm font-body ${hasError ? 'text-error' : 'text-on-surface-variant'}`}
        >
          {errorText || supportingText}
        </p>
      )}
    </div>
  );
};
