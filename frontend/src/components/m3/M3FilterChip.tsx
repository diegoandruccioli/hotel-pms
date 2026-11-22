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
        // Visually 30 px with a mouse (list toolbars stay dense), but the hit area reaches 40 px through
        // an invisible ::after; 44 px tall and wide on small or touch screens.
        'inline-flex items-center justify-center min-h-11 min-w-11 sm:pointer-fine:min-h-0 px-3 py-1.5 rounded-full text-xs font-medium font-body border transition-colors',
        'sm:pointer-fine:relative sm:pointer-fine:after:absolute sm:pointer-fine:after:inset-x-0 sm:pointer-fine:after:-inset-y-1.5',
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
