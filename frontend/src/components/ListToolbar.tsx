import type { ChangeEvent, ReactNode } from 'react';
import { M3TextField } from './m3';
import { cn } from '../utils';

interface ListToolbarProps {
  /** Accessible name of the search field (also shown as the placeholder). */
  searchLabel: string;
  searchValue: string;
  onSearchChange: (e: ChangeEvent<HTMLInputElement>) => void;
  /** Names the chip row for assistive tech (rendered as a group). */
  filtersLabel?: string;
  /** Filter chips, next to the search field. */
  children?: ReactNode;
  /** Right-aligned actions (e.g. export). */
  trailing?: ReactNode;
  className?: string;
}

/** Row under a list page's `PageHeader`: search, filter chips and secondary actions.
 * Wraps on narrow screens, with the search field taking the full width. */
export const ListToolbar = ({
  searchLabel,
  searchValue,
  onSearchChange,
  filtersLabel,
  children,
  trailing,
  className,
}: ListToolbarProps) => (
  <div className={cn('flex flex-wrap items-center gap-3', className)}>
    <M3TextField
      label={searchLabel}
      hideLabel
      leadingIcon="search"
      type="search"
      value={searchValue}
      onChange={onSearchChange}
      className="w-full sm:w-72"
    />
    {children && (
      <div role={filtersLabel ? 'group' : undefined} aria-label={filtersLabel} className="flex flex-wrap items-center gap-2">
        {children}
      </div>
    )}
    {trailing && <div className="flex flex-wrap items-center gap-2 sm:ml-auto">{trailing}</div>}
  </div>
);
