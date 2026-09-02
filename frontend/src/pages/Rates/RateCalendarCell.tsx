import { memo, useCallback } from 'react';
import type { RateCalendarDay } from '../../types/inventory.types';

interface RateCalendarCellProps {
  day: RateCalendarDay;
  rowIndex: number;
  dayIndex: number;
  isSelected: boolean;
  isToday: boolean;
  seasonColor?: string;
  ariaLabel: string;
  onPointerDown: (rowIndex: number, dayIndex: number) => void;
  onPointerEnter: (rowIndex: number, dayIndex: number) => void;
  onKeyboardSelect: (rowIndex: number, dayIndex: number, extend: boolean) => void;
}

/**
 * One resolved-price cell in the rate calendar grid. Supports two independent
 * selection paths so the whole grid stays TAB-only operable (CLAUDE.md):
 * mouse drag (pointerdown -> pointerenter -> window pointerup, wired by the
 * parent) and keyboard (Enter/Space to start a selection, Shift+Enter/Space
 * to extend it — a keyboard-triggered click carries `detail === 0`, which is
 * how this tells the two input sources apart without double-handling a
 * plain mouse click).
 */
export const RateCalendarCell = memo(({
  day, rowIndex, dayIndex, isSelected, isToday, seasonColor, ariaLabel,
  onPointerDown, onPointerEnter, onKeyboardSelect,
}: RateCalendarCellProps) => {
  const handlePointerDown = useCallback(
    () => onPointerDown(rowIndex, dayIndex),
    [onPointerDown, rowIndex, dayIndex],
  );
  const handlePointerEnter = useCallback(
    () => onPointerEnter(rowIndex, dayIndex),
    [onPointerEnter, rowIndex, dayIndex],
  );
  const handleClick = useCallback((e: React.MouseEvent<HTMLButtonElement>) => {
    if (e.detail === 0) {
      onKeyboardSelect(rowIndex, dayIndex, e.shiftKey);
    }
  }, [onKeyboardSelect, rowIndex, dayIndex]);

  const hasSeason = day.rateSeasonId != null;
  const style = hasSeason ? { borderLeftColor: seasonColor, borderLeftWidth: 4 } : undefined;
  const dotStyle = hasSeason ? { backgroundColor: seasonColor } : undefined;

  return (
    <button
      type="button"
      onPointerDown={handlePointerDown}
      onPointerEnter={handlePointerEnter}
      onClick={handleClick}
      aria-pressed={isSelected}
      aria-label={ariaLabel}
      style={style}
      className={`shrink-0 w-[100px] h-16 border-r border-b border-outline-variant flex flex-col items-center justify-center gap-1 text-xs font-body transition-colors focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-inset ${
        isSelected
          ? 'bg-primary-container'
          : isToday
            ? 'bg-surface-container'
            : 'bg-surface hover:bg-surface-container-low'
      }`}
    >
      {hasSeason && <span className="w-2 h-2 rounded-full" style={dotStyle} aria-hidden="true" />}
      <span className="font-medium text-on-surface">€ {day.price.toFixed(0)}</span>
    </button>
  );
});

RateCalendarCell.displayName = 'RateCalendarCell';
