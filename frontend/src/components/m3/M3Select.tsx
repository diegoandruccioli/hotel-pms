import { useId } from 'react';

export interface M3SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

interface M3SelectProps extends Omit<React.SelectHTMLAttributes<HTMLSelectElement>, 'id'> {
  label: string;
  options: M3SelectOption[];
  /** Renders a leading disabled option (e.g. "Select a room type…") when the
   * field has no default value. Distinct from `options` so callers don't
   * have to remember to mark it `disabled` themselves every time. */
  placeholder?: string;
  errorText?: string;
  supportingText?: string;
}

/**
 * A native <select> is unaffected by the floating-label treatment
 * M3TextField uses for text inputs — every existing hand-rolled <select> in
 * the app (10 files) already used a static label above the field instead,
 * so this follows that established convention rather than inventing a new
 * one.
 */
export const M3Select = ({
  label,
  options,
  placeholder,
  errorText,
  supportingText,
  className = '',
  required,
  ...rest
}: M3SelectProps) => {
  const id = useId();
  const hasError = !!errorText;

  return (
    <div className={className}>
      <label htmlFor={id} className="block text-sm font-medium font-body text-on-surface-variant mb-1">
        {label}
        {required && ' *'}
      </label>
      <select
        id={id}
        required={required}
        aria-invalid={hasError}
        aria-describedby={errorText ? `${id}-error` : supportingText ? `${id}-support` : undefined}
        className={`w-full px-4 py-2.5 rounded-shape-xs border bg-transparent text-sm font-body text-on-surface transition-all focus:outline-none
          ${hasError
            ? 'border-error ring-2 ring-error ring-offset-1'
            : 'border-outline hover:border-on-surface focus:border-primary focus:ring-2 focus:ring-primary focus:ring-offset-1'
          }`}
        {...rest}
      >
        {placeholder && (
          <option value="" disabled>
            {placeholder}
          </option>
        )}
        {options.map((option) => (
          <option key={option.value} value={option.value} disabled={option.disabled}>
            {option.label}
          </option>
        ))}
      </select>
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
