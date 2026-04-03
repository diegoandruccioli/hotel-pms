interface M3TableProps {
  headers: React.ReactNode[];
  children: React.ReactNode;
  className?: string;
}

export const M3Table = ({ headers, children, className = '' }: M3TableProps) => (
  <div className={`bg-surface shadow-elevation-1 rounded-shape-md overflow-hidden ${className}`}>
    <div className="overflow-x-auto">
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
    className={`hover:bg-surface-container-low transition-colors ${className}`}
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
    className={`whitespace-nowrap py-4 px-4 text-sm font-body text-on-surface first:pl-6 last:pr-6 ${className}`}
    {...rest}
  >
    {children}
  </td>
);
