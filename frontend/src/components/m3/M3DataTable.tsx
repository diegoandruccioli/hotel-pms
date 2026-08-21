import { useCallback, useMemo, useRef, memo, type ReactElement, type ReactNode, type RefObject } from 'react';
import {
  type ColumnDef,
  type Row,
  type SortingState,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { useVirtualizer } from '@tanstack/react-virtual';
import { MaterialIcon } from '../MaterialIcon';
import { M3TableEmptyRow } from './M3EmptyState';
import { cn } from '../../utils/cn';

/** CLAUDE.md mandates virtual scrolling past this row count. Every page
 * migrated to M3DataTable so far paginates well under this (20/page), so in
 * practice this only ever engages for an unpaginated list that grows past
 * it — the threshold is a safety net, not something these pages hit today. */
const VIRTUALIZATION_THRESHOLD = 50;
const ESTIMATED_ROW_HEIGHT = 57;
const VIRTUALIZED_VIEWPORT_HEIGHT = 600;

export interface M3DataTableProps<T> {
  data: T[];
  columns: ColumnDef<T>[];
  /** Row id, used both by react-table and as the React `key` — falls back to
   * array index when omitted (fine for pages that never reorder in place). */
  getRowId?: (row: T, index: number) => string;
  /** Controlled, single-column sort state. Server-driven — this component
   * never reorders `data` itself, it only reports what the caller's next
   * `sort=` query param should be (see Fase 1's search endpoints). */
  sorting: SortingState;
  onSortingChange: (sorting: SortingState) => void;
  emptyMessage: string;
  className?: string;
}

/**
 * Clicking an inactive column sorts it ascending; clicking the already-active
 * column toggles asc/desc. Deliberately a 2-state toggle once a column is
 * active rather than a 3-state asc/desc/none cycle: every page migrated to
 * `M3DataTable` so far always has exactly one active sort (their backend
 * `Pageable` always applies a default), so a reachable "no sort" state would
 * just be a dead end the caller has to special-case back to some default.
 */
function nextSortDirection(current: false | 'asc' | 'desc'): 'asc' | 'desc' {
  if (current === 'asc') return 'desc';
  return 'asc';
}

/**
 * Headless-table-powered replacement for the bare `M3Table` on pages that
 * need sortable, keyboard-navigable columns (Fase 2.1) — built on
 * `@tanstack/react-table` for the sorting model and `@tanstack/react-virtual`
 * for the row window past {@link VIRTUALIZATION_THRESHOLD} rows. Renders the
 * same `<table>`/`<th>`/`<td>` markup and classes `M3Table` uses, so the two
 * are visually interchangeable.
 */
export function M3DataTable<T>({
  data,
  columns,
  getRowId,
  sorting,
  onSortingChange,
  emptyMessage,
  className = '',
}: M3DataTableProps<T>) {
  // react-table's table instance carries methods the compiler can't prove
  // are safe to auto-memoize — opts this component out of compilation
  // rather than risk stale closures; the memoization that matters here
  // (SortableHeaderCell, DataRow) is already explicit below. The eslint
  // rule flags the same thing statically and doesn't know about the
  // directive above, so it needs its own suppression.
  'use no memo';

  const scrollRef = useRef<HTMLDivElement>(null);

  // eslint-disable-next-line react-hooks/incompatible-library -- see 'use no memo' above
  const table = useReactTable({
    data,
    columns,
    state: { sorting },
    manualSorting: true,
    // react-table only treats `state.sorting` as authoritative for reads
    // like getIsSorted() once a matching onSortingChange is also wired up —
    // without it, some internal getters fall back to react-table's own
    // (unused, always-empty) internal sorting state. The actual toggle
    // logic lives in handleToggleSort below; this just satisfies that.
    onSortingChange: () => {},
    getRowId: getRowId as ((row: T, index: number) => string) | undefined,
    getCoreRowModel: getCoreRowModel(),
  });

  const rows = table.getRowModel().rows;
  const virtualize = rows.length > VIRTUALIZATION_THRESHOLD;

  const scrollContainerStyle = useMemo(
    () => (virtualize ? { overflowY: 'auto' as const, maxHeight: VIRTUALIZED_VIEWPORT_HEIGHT } : undefined),
    [virtualize],
  );

  const handleToggleSort = useCallback((columnId: string, currentDirection: false | 'asc' | 'desc') => {
    const next = nextSortDirection(currentDirection);
    onSortingChange([{ id: columnId, desc: next === 'desc' }]);
  }, [onSortingChange]);

  return (
    <div className={cn('bg-surface shadow-elevation-1 rounded-shape-md overflow-hidden', className)}>
      <div ref={scrollRef} className="overflow-x-auto" style={scrollContainerStyle}>
        <table className="min-w-full" aria-rowcount={virtualize ? rows.length + 1 : undefined}>
          <thead className={virtualize ? 'sticky top-0 z-10' : undefined}>
            <tr className="bg-surface-container-highest">
              {table.getFlatHeaders().map((header) => (
                <SortableHeaderCell
                  key={header.id}
                  columnId={header.column.id}
                  label={flexRender(header.column.columnDef.header, header.getContext())}
                  canSort={header.column.getCanSort()}
                  direction={header.column.getIsSorted()}
                  onToggleSort={handleToggleSort}
                />
              ))}
            </tr>
          </thead>
          {rows.length === 0 ? (
            <tbody>
              <M3TableEmptyRow colSpan={columns.length} message={emptyMessage} />
            </tbody>
          ) : virtualize ? (
            <VirtualizedBody rows={rows} columnCount={columns.length} scrollRef={scrollRef} />
          ) : (
            <tbody className="divide-y divide-outline-variant/50">
              {rows.map((row) => (
                <DataRow key={row.id} row={row} />
              ))}
            </tbody>
          )}
        </table>
      </div>
    </div>
  );
}

interface SortableHeaderCellProps {
  columnId: string;
  label: ReactNode;
  canSort: boolean;
  direction: false | 'asc' | 'desc';
  onToggleSort: (columnId: string, currentDirection: false | 'asc' | 'desc') => void;
}

/**
 * Deliberately takes `direction`/`canSort` as plain props computed by the
 * parent, rather than a react-table `Header` object it reads itself: v8
 * caches header objects across renders when the underlying columns/data
 * haven't changed, so a `header` prop can stay referentially identical
 * while `header.column.getIsSorted()` — reading live table-wide sorting
 * state through a closure — would return something different. `memo`'s
 * reference-equality bailout can't see that, and silently keeps rendering
 * the stale sort indicator. Passing the already-resolved value sidesteps it.
 */
const SortableHeaderCell = memo(({ columnId, label, canSort, direction, onToggleSort }: SortableHeaderCellProps) => {
  const handleClick = useCallback(() => {
    onToggleSort(columnId, direction);
  }, [onToggleSort, columnId, direction]);

  return (
    <th
      scope="col"
      aria-sort={!canSort ? undefined : direction === 'asc' ? 'ascending' : direction === 'desc' ? 'descending' : 'none'}
      className="py-3.5 px-4 text-left text-xs font-medium font-body text-on-surface-variant uppercase tracking-wider first:pl-6 last:pr-6"
    >
      {canSort ? (
        <button
          type="button"
          onClick={handleClick}
          className="inline-flex items-center gap-1 hover:text-on-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded"
        >
          {label}
          <MaterialIcon
            name={direction === 'asc' ? 'arrow_upward' : direction === 'desc' ? 'arrow_downward' : 'unfold_more'}
            size={14}
            className={direction ? 'text-primary' : 'text-on-surface-variant/50'}
          />
        </button>
      ) : label}
    </th>
  );
});

const DataRow = memo(<T,>({ row }: { row: Row<T> }) => (
  <tr className="hover:bg-surface-container-low transition-colors">
    {row.getVisibleCells().map((cell) => (
      <td
        key={cell.id}
        className="whitespace-nowrap py-4 px-4 text-sm font-body text-on-surface first:pl-6 last:pr-6"
      >
        {flexRender(cell.column.columnDef.cell, cell.getContext())}
      </td>
    ))}
  </tr>
)) as <T>(props: { row: Row<T> }) => ReactElement;

/**
 * Row windowing for the `<table>` case, following TanStack Virtual's own
 * table recipe: real `<tr>`/`<td>` elements (so native table/row/cell ARIA
 * semantics apply with no manual role juggling), with a leading and trailing
 * spacer `<tr>` standing in for the rows scrolled past instead of absolute
 * positioning — a `<tbody>` can only contain `<tr>` children, so this is the
 * valid-HTML way to reserve the virtual list's total scroll height.
 */
function VirtualizedBody<T>({
  rows,
  columnCount,
  scrollRef,
}: {
  rows: Row<T>[];
  columnCount: number;
  scrollRef: RefObject<HTMLDivElement | null>;
}) {
  // Same reasoning as M3DataTable above — react-virtual's virtualizer
  // instance is the same kind of opaque, method-bearing object.
  'use no memo';

  // eslint-disable-next-line react-hooks/incompatible-library -- see 'use no memo' above
  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => scrollRef.current,
    estimateSize: () => ESTIMATED_ROW_HEIGHT,
    overscan: 10,
  });
  const virtualRows = virtualizer.getVirtualItems();
  const paddingTop = virtualRows.length > 0 ? virtualRows[0].start : 0;
  const paddingBottom = virtualRows.length > 0
    ? virtualizer.getTotalSize() - virtualRows[virtualRows.length - 1].end
    : 0;
  const topSpacerStyle = useMemo(() => ({ height: paddingTop, padding: 0, border: 0 }), [paddingTop]);
  const bottomSpacerStyle = useMemo(() => ({ height: paddingBottom, padding: 0, border: 0 }), [paddingBottom]);

  return (
    <tbody className="divide-y divide-outline-variant/50">
      {paddingTop > 0 && (
        <tr aria-hidden="true"><td colSpan={columnCount} style={topSpacerStyle} /></tr>
      )}
      {virtualRows.map((virtualRow) => {
        const row = rows[virtualRow.index];
        return (
          <tr key={row.id} aria-rowindex={virtualRow.index + 2} className="hover:bg-surface-container-low transition-colors">
            {row.getVisibleCells().map((cell) => (
              <td
                key={cell.id}
                className="whitespace-nowrap py-4 px-4 text-sm font-body text-on-surface first:pl-6 last:pr-6"
              >
                {flexRender(cell.column.columnDef.cell, cell.getContext())}
              </td>
            ))}
          </tr>
        );
      })}
      {paddingBottom > 0 && (
        <tr aria-hidden="true"><td colSpan={columnCount} style={bottomSpacerStyle} /></tr>
      )}
    </tbody>
  );
}
