import { useCallback, type ButtonHTMLAttributes } from 'react';
import { cn } from '../../utils';

export interface M3FilterChipProps<T extends string>
  extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'type' | 'children' | 'value'> {
  label: string;
  selected: boolean;
  /** Value reported to `onValueSelect` — lets a list of chips share one stable handler. */
  value?: T;
  onValueSelect?: (value: T) => void;
}

/**
 * M3 filter chip: a pill toggle used to narrow a list (status row, quick filters).
 * Exposes its state through aria-pressed. Use `onClick` for a standalone toggle, or
 * `value` + `onValueSelect` for a row of chips driven by one handler.
 */
export function M3FilterChip<T extends string = string>({
  label,
  selected,
  value,
  onValueSelect,
  onClick,
  className,
  ...rest
}: M3FilterChipProps<T>) {
  const handleClick = useCallback(
    (e: React.MouseEvent<HTMLButtonElement>) => {
      onClick?.(e);
      if (value !== undefined) onValueSelect?.(value);
    },
    [onClick, onValueSelect, value]
  );

  return (
    <button
      {...rest}
      type="button"
      aria-pressed={selected}
      onClick={handleClick}
      className={cn(
        'px-3 py-1.5 rounded-full text-xs font-medium font-body border transition-colors',
        selected
          ? 'bg-primary text-on-primary border-primary'
          : 'bg-transparent text-on-surface-variant border-outline-variant hover:border-outline',
        className
      )}
    >
      {label}
    </button>
  );
}
