import { cn } from '../../utils';

/**
 * Pins a table's last column (its row actions) to the right edge while the rest scrolls sideways,
 * so the actions stay in view on a narrow screen. The sticky cells get an opaque background so
 * scrolled content does not show through, and follow the row hover. Written as literal classes so
 * Tailwind's scanner finds them; shared with `M3DataTable`.
 */
export const STICKY_END_COLUMN = [
  '[&_th:last-child]:sticky [&_th:last-child]:right-0 [&_th:last-child]:z-1 [&_th:last-child]:bg-surface-container-highest',
  // `:not([colspan])`: the empty-state row's single full-width cell is also a last child, but is no actions cell.
  '[&_td:last-child:not([colspan])]:sticky [&_td:last-child:not([colspan])]:right-0 [&_td:last-child:not([colspan])]:z-1',
  '[&_td:last-child:not([colspan])]:bg-surface-container-lowest',
  // Same `(hover: hover)` guard as Tailwind's own `hover:`, or a tap leaves the pinned cell tinted on a phone.
  '[@media(hover:hover)]:[&_tr:hover_td:last-child:not([colspan])]:bg-surface-container-low',
  // Focus that scrolls a control into view must not stop under the pinned column (WCAG 2.4.11).
  'scroll-pe-32',
].join(' ');

interface M3TableProps {
  headers: React.ReactNode[];
  children: React.ReactNode;
  className?: string;
  /** Keep the last column (the row actions) in view while the table scrolls sideways. */
  stickyEnd?: boolean;
}

export const M3Table = ({ headers, children, className = '', stickyEnd = false }: M3TableProps) => (
  <div className={cn('bg-surface-container-lowest border border-outline-variant shadow-elevation-1 rounded-shape-lg overflow-hidden', className)}>
    <div className={cn('overflow-x-auto', stickyEnd && STICKY_END_COLUMN)}>
      <table className="min-w-full">
        <thead>
          <tr className="bg-surface-container-highest">
            {headers.map((header, index) => (
              <th
                key={index}
                scope="col"
                className="py-3.5 px-4 text-left text-xs font-medium font-body text-on-surface-variant uppercase tracking-wider first:pl-6 last:pr-6"
              >
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-outline-variant/50">
          {children}
        </tbody>
      </table>
    </div>
  </div>
);

interface M3TableRowProps extends React.HTMLAttributes<HTMLTableRowElement> {
  children: React.ReactNode;
}

export const M3TableRow = ({ children, className = '', ...rest }: M3TableRowProps) => (
  <tr
    className={cn('hover:bg-surface-container-low transition-colors', className)}
    {...rest}
  >
    {children}
  </tr>
);

export const M3TableCell = ({
  children,
  className = '',
  ...rest
}: React.TdHTMLAttributes<HTMLTableCellElement>) => (
  <td
    className={cn('whitespace-nowrap py-4 px-4 text-sm font-body text-on-surface first:pl-6 last:pr-6', className)}
    {...rest}
  >
    {children}
  </td>
);
