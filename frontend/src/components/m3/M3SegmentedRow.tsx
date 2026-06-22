import { useCallback, memo, type ReactElement } from 'react';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../MaterialIcon';

export interface M3SegmentOption<T extends string> {
  value: T;
  labelKey: string;
  icon: string;
}

/**
 * Single button inside an M3SegmentedRow.
 * Extracted to avoid creating new functions as props on every render.
 */
const SegmentedButton = memo(function SegmentedButton<T extends string>({
  opt,
  isActive,
  isFirst,
  isLast,
  onChange,
}: {
  opt: M3SegmentOption<T>;
  isActive: boolean;
  isFirst: boolean;
  isLast: boolean;
  onChange: (v: T) => void;
}) {
  const { t } = useTranslation('settings');
  const handleClick = useCallback(() => onChange(opt.value), [onChange, opt.value]);

  return (
    <button
      type="button"
      role="radio"
      aria-checked={isActive}
      onClick={handleClick}
      className={[
        'relative flex items-center justify-center gap-1.5',
        'flex-1 h-10 px-3 text-sm font-medium font-body',
        'focus-visible:outline-none focus-visible:z-10',
        'focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-inset',
        'transition-colors',
        isFirst ? 'rounded-l-shape-full' : '',
        isLast ? 'rounded-r-shape-full' : '',
        isActive
          ? 'bg-secondary-container text-on-secondary-container'
          : 'bg-surface text-on-surface-variant hover:bg-surface-container-high',
        !isFirst ? 'border-l border-outline' : '',
      ].join(' ')}
      aria-label={t(opt.labelKey)}
    >
      {isActive && <MaterialIcon name="check" size={16} className="shrink-0" />}
      <span className="truncate">{t(opt.labelKey)}</span>
    </button>
  );
}) as <T extends string>(props: {
  opt: M3SegmentOption<T>;
  isActive: boolean;
  isFirst: boolean;
  isLast: boolean;
  onChange: (v: T) => void;
}) => ReactElement;

/**
 * M3 Segmented-button row (mutual exclusion).
 * Each option shows a check icon when selected.
 */
export function M3SegmentedRow<T extends string>({
  options,
  value,
  onChange,
  ariaLabel,
}: {
  options: M3SegmentOption<T>[];
  value: T;
  onChange: (v: T) => void;
  ariaLabel: string;
}) {
  return (
    <div
      role="radiogroup"
      aria-label={ariaLabel}
      className="flex rounded-shape-full border border-outline overflow-hidden"
    >
      {options.map((opt, idx) => (
        <SegmentedButton
          key={opt.value}
          opt={opt}
          isActive={opt.value === value}
          isFirst={idx === 0}
          isLast={idx === options.length - 1}
          onChange={onChange}
        />
      ))}
    </div>
  );
}
