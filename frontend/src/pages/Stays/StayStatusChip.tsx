import { useCallback, memo } from 'react';
import type { StayStatus } from '../../types';

// ---------------------------------------------------------------------------
// StayStatusChip — filter chip for the status row above the Stays table.
// ---------------------------------------------------------------------------
export interface StayStatusChipProps {
  value: StayStatus | 'ALL';
  active: boolean;
  label: string;
  onClick: (v: StayStatus | 'ALL') => void;
}

export const StayStatusChip = memo(({ value, active, label, onClick }: StayStatusChipProps) => {
  const handleClick = useCallback(() => onClick(value), [onClick, value]);
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={handleClick}
      className={`px-3 py-1.5 rounded-full text-xs font-medium font-body border transition-colors ${
        active
          ? 'bg-primary text-on-primary border-primary'
          : 'bg-transparent text-on-surface-variant border-outline-variant hover:border-outline'
      }`}
    >
      {label}
    </button>
  );
});
StayStatusChip.displayName = 'StayStatusChip';
