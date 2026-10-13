import { useCallback, memo } from 'react';
import { MaterialIcon } from '../MaterialIcon';
import { cn } from '../../utils';

export interface M3StepperStep {
  id: string;
  label: string;
}

export interface M3StepperProps {
  steps: readonly M3StepperStep[];
  /** Zero-based index of the step being shown. */
  current: number;
  /** Whether the user may jump to a step; non-selectable steps render as plain text. */
  isSelectable: (index: number) => boolean;
  onSelect: (index: number) => void;
  ariaLabel: string;
  /** Text shown instead of the labels on narrow screens, e.g. "Step 2 of 4: Rooms". */
  progressLabel: string;
  className?: string;
}

interface StepItemProps {
  index: number;
  label: string;
  state: 'done' | 'current' | 'upcoming';
  selectable: boolean;
  onSelect: (index: number) => void;
}

const StepItem = memo(({ index, label, state, selectable, onSelect }: StepItemProps) => {
  const handleClick = useCallback(() => onSelect(index), [onSelect, index]);
  const filled = state !== 'upcoming';

  const content = (
    <>
      <span
        aria-hidden="true"
        className={cn(
          'flex h-8 w-8 shrink-0 items-center justify-center rounded-full border text-sm font-medium font-body',
          filled
            ? 'bg-primary text-on-primary border-primary'
            : 'bg-transparent text-on-surface-variant border-outline'
        )}
      >
        {state === 'done' ? <MaterialIcon name="check" size={18} /> : index + 1}
      </span>
      <span
        className={cn(
          'sr-only sm:not-sr-only text-sm font-body',
          state === 'current' ? 'font-semibold text-on-surface' : 'font-medium text-on-surface-variant'
        )}
      >
        {label}
      </span>
    </>
  );

  const itemClass = 'flex min-h-11 items-center gap-2 rounded-full';

  return (
    <li className="flex items-center">
      {selectable ? (
        <button
          type="button"
          onClick={handleClick}
          aria-current={state === 'current' ? 'step' : undefined}
          className={cn(itemClass, 'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary')}
        >
          {content}
        </button>
      ) : (
        <span
          aria-current={state === 'current' ? 'step' : undefined}
          className={itemClass}
        >
          {content}
        </span>
      )}
    </li>
  );
});

StepItem.displayName = 'StepItem';

/**
 * M3 stepper: progress indicator for a multi-step creation flow. Completed steps show a
 * check, the current one carries aria-current="step". Which steps can be jumped to is up
 * to the caller (sequential for "new", free for "edit"). Below `sm` only the numbered
 * circles and `progressLabel` are shown, so it never scrolls horizontally.
 */
export const M3Stepper = memo(({
  steps,
  current,
  isSelectable,
  onSelect,
  ariaLabel,
  progressLabel,
  className,
}: M3StepperProps) => (
  <div className={className}>
    <ol aria-label={ariaLabel} className="flex flex-wrap items-center gap-x-2 gap-y-1">
      {steps.map((step, index) => (
        <StepItem
          key={step.id}
          index={index}
          label={step.label}
          state={index < current ? 'done' : index === current ? 'current' : 'upcoming'}
          selectable={isSelectable(index)}
          onSelect={onSelect}
        />
      ))}
    </ol>
    <p className="sm:hidden mt-2 text-sm font-body text-on-surface-variant">{progressLabel}</p>
  </div>
));

M3Stepper.displayName = 'M3Stepper';
