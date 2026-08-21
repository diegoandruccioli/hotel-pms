/* eslint-disable react-perf/jsx-no-new-array-as-prop */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import type { ColumnDef, SortingState } from '@tanstack/react-table';
import { M3DataTable } from './M3DataTable';

interface TestRow {
  id: string;
  name: string;
  value: number;
}

const COLUMNS: ColumnDef<TestRow>[] = [
  { id: 'name', accessorKey: 'name', header: 'Name' },
  { id: 'value', accessorKey: 'value', header: 'Value', enableSorting: false },
];

const ROWS: TestRow[] = [
  { id: '1', name: 'Alice', value: 10 },
  { id: '2', name: 'Bob', value: 20 },
];

function manyRows(count: number): TestRow[] {
  return Array.from({ length: count }, (_, i) => ({ id: String(i), name: `Row ${i}`, value: i }));
}

describe('M3DataTable', () => {
  it('renders headers and row data', () => {
    render(
      <M3DataTable
        data={ROWS}
        columns={COLUMNS}
        sorting={[]}
        onSortingChange={vi.fn()}
        emptyMessage="No rows"
      />,
    );
    expect(screen.getByText('Name')).toBeInTheDocument();
    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Bob')).toBeInTheDocument();
  });

  it('shows the empty message when data is empty', () => {
    render(
      <M3DataTable
        data={[]}
        columns={COLUMNS}
        sorting={[]}
        onSortingChange={vi.fn()}
        emptyMessage="No rows"
      />,
    );
    expect(screen.getByText('No rows')).toBeInTheDocument();
  });

  it('does not render a sort button for a column with enableSorting: false', () => {
    render(
      <M3DataTable
        data={ROWS}
        columns={COLUMNS}
        sorting={[]}
        onSortingChange={vi.fn()}
        emptyMessage="No rows"
      />,
    );
    expect(screen.queryByRole('button', { name: /Value/ })).not.toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Value' })).not.toHaveAttribute('aria-sort');
  });

  it('selects a column ascending on first click, then toggles asc/desc on repeated clicks', () => {
    const onSortingChange = vi.fn();
    const { rerender } = render(
      <M3DataTable
        data={ROWS}
        columns={COLUMNS}
        sorting={[]}
        onSortingChange={onSortingChange}
        emptyMessage="No rows"
      />,
    );

    const sortButton = screen.getByRole('button', { name: /Name/ });
    fireEvent.click(sortButton);
    expect(onSortingChange).toHaveBeenLastCalledWith([{ id: 'name', desc: false }] satisfies SortingState);

    rerender(
      <M3DataTable
        data={ROWS}
        columns={COLUMNS}
        sorting={[{ id: 'name', desc: false }]}
        onSortingChange={onSortingChange}
        emptyMessage="No rows"
      />,
    );
    expect(screen.getByRole('columnheader', { name: /Name/ })).toHaveAttribute('aria-sort', 'ascending');

    fireEvent.click(screen.getByRole('button', { name: /Name/ }));
    expect(onSortingChange).toHaveBeenLastCalledWith([{ id: 'name', desc: true }] satisfies SortingState);

    rerender(
      <M3DataTable
        data={ROWS}
        columns={COLUMNS}
        sorting={[{ id: 'name', desc: true }]}
        onSortingChange={onSortingChange}
        emptyMessage="No rows"
      />,
    );
    expect(screen.getByRole('columnheader', { name: /Name/ })).toHaveAttribute('aria-sort', 'descending');

    fireEvent.click(screen.getByRole('button', { name: /Name/ }));
    expect(onSortingChange).toHaveBeenLastCalledWith([{ id: 'name', desc: false }] satisfies SortingState);
  });

  it('virtualizes past the row threshold, exposing aria-rowcount and rendering fewer DOM rows than data', () => {
    const rows = manyRows(200);
    render(
      <M3DataTable
        data={rows}
        columns={COLUMNS}
        sorting={[]}
        onSortingChange={vi.fn()}
        emptyMessage="No rows"
      />,
    );
    const table = screen.getByRole('table');
    expect(table).toHaveAttribute('aria-rowcount', '201');
    // jsdom reports zero layout size, so react-virtual only measures the
    // overscan window around index 0 — this asserts windowing kicked in at
    // all, not an exact visible count (which depends on real layout).
    expect(screen.getAllByRole('row').length).toBeLessThan(rows.length);
  });

  it('does not virtualize at or under the row threshold', () => {
    const rows = manyRows(50);
    render(
      <M3DataTable
        data={rows}
        columns={COLUMNS}
        sorting={[]}
        onSortingChange={vi.fn()}
        emptyMessage="No rows"
      />,
    );
    expect(screen.getByRole('table')).not.toHaveAttribute('aria-rowcount');
    expect(screen.getAllByText(/^Row \d+$/)).toHaveLength(50);
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <M3DataTable
        data={ROWS}
        columns={COLUMNS}
        sorting={[{ id: 'name', desc: false }]}
        onSortingChange={vi.fn()}
        emptyMessage="No rows"
      />,
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
