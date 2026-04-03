/* eslint-disable react-perf/jsx-no-new-array-as-prop */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { M3Table, M3TableRow, M3TableCell } from './M3Table';

describe('M3Table', () => {
  it('should render headers', () => {
    render(
      <M3Table headers={['Name', 'Status', 'Actions']}>
        <M3TableRow>
          <M3TableCell>John</M3TableCell>
          <M3TableCell>Active</M3TableCell>
          <M3TableCell>Edit</M3TableCell>
        </M3TableRow>
      </M3Table>
    );

    expect(screen.getByText('Name')).toBeInTheDocument();
    expect(screen.getByText('Status')).toBeInTheDocument();
    expect(screen.getByText('Actions')).toBeInTheDocument();
  });

  it('should render table rows with cells', () => {
    render(
      <M3Table headers={['Col']}>
        <M3TableRow>
          <M3TableCell>Cell content</M3TableCell>
        </M3TableRow>
      </M3Table>
    );

    expect(screen.getByText('Cell content')).toBeInTheDocument();
  });

  it('should apply scope=col to header cells', () => {
    render(
      <M3Table headers={['Header']}>
        <tr><td>Body</td></tr>
      </M3Table>
    );

    const th = screen.getByText('Header');
    expect(th).toHaveAttribute('scope', 'col');
  });

  it('should pass additional className', () => {
    render(
      <M3Table headers={['H']} className="mt-4">
        <tr><td>B</td></tr>
      </M3Table>
    );

    const container = screen.getByText('H').closest('div.overflow-x-auto')?.parentElement;
    expect(container?.className).toContain('mt-4');
  });
});
