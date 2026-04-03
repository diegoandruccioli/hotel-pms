import { useState, useId, useCallback } from 'react';

interface M3TextFieldProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'id'> {
  label: string;
  supportingText?: string;
  errorText?: string;
  leadingIcon?: string;
}

const ICON_STYLE = { fontSize: 20 };

export const M3TextField = ({
  label,
  supportingText,
  errorText,
  leadingIcon,
  className = '',
  onFocus,
  onBlur,
  ...rest
}: M3TextFieldProps) => {
  const id = useId();
  const [focused, setFocused] = useState(false);
  const hasValue = !!rest.value && String(rest.value).length > 0;
  const isFloating = focused || hasValue;
  const hasError = !!errorText;

  const handleFocus = useCallback((e: React.FocusEvent<HTMLInputElement>) => {
    setFocused(true);
    onFocus?.(e);
  }, [onFocus]);

  const handleBlur = useCallback((e: React.FocusEvent<HTMLInputElement>) => {
    setFocused(false);
    onBlur?.(e);
  }, [onBlur]);

  return (
    <div className={`relative ${className}`}>
      {/* Input Container */}
      <div
        className={`relative flex items-center rounded-shape-xs border transition-colors
          ${hasError
            ? 'border-error'
            : focused
              ? 'border-primary border-2'
              : 'border-outline hover:border-on-surface'
          }
        `}
      >
        {leadingIcon && (
          <span className="material-symbols-outlined pl-3 text-on-surface-variant" style={ICON_STYLE}>
            {leadingIcon}
          </span>
        )}

        <input
          id={id}
          className={`peer w-full bg-transparent px-4 pt-5 pb-1.5 text-sm font-body text-on-surface placeholder-transparent
            focus:outline-none ${leadingIcon ? 'pl-2' : ''}`}
          placeholder={label}
          onFocus={handleFocus}
          onBlur={handleBlur}
          aria-invalid={hasError}
          aria-describedby={errorText ? `${id}-error` : supportingText ? `${id}-support` : undefined}
          {...rest}
        />

        {/* Floating Label */}
        <label
          htmlFor={id}
          className={`absolute transition-all duration-150 pointer-events-none font-body
            ${leadingIcon ? 'left-10' : 'left-4'}
            ${isFloating
              ? 'top-1 text-xs'
              : 'top-1/2 -translate-y-1/2 text-sm'
            }
            ${hasError
              ? 'text-error'
              : focused
                ? 'text-primary'
                : 'text-on-surface-variant'
            }
          `}
        >
          {label}
        </label>
      </div>

      {/* Supporting / Error Text */}
      {(errorText || supportingText) && (
        <p
          id={errorText ? `${id}-error` : `${id}-support`}
          className={`mt-1 px-4 text-xs font-body ${hasError ? 'text-error' : 'text-on-surface-variant'}`}
        >
          {errorText || supportingText}
        </p>
      )}
    </div>
  );
};
