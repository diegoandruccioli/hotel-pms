import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { M3EmptyState, M3TableEmptyRow } from './M3EmptyState';

describe('M3EmptyState', () => {
  it('renders the icon and title', () => {
    render(<M3EmptyState icon="group_off" title="No guests found" />);
    expect(screen.getByText('group_off')).toBeInTheDocument();
    expect(screen.getByText('No guests found')).toBeInTheDocument();
  });

  it('renders the description when provided', () => {
    render(<M3EmptyState icon="group_off" title="No guests found" description="Try a different search." />);
    expect(screen.getByText('Try a different search.')).toBeInTheDocument();
  });

  it('omits the description paragraph when not provided', () => {
    render(<M3EmptyState icon="group_off" title="No guests found" />);
    expect(screen.queryByText('Try a different search.')).not.toBeInTheDocument();
  });

  it('renders the action node when provided', () => {
    render(<M3EmptyState icon="group_off" title="No guests found" action={<button type="button">Add guest</button>} />);
    expect(screen.getByRole('button', { name: 'Add guest' })).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <M3EmptyState icon="group_off" title="No guests found" description="Try a different search." />,
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});

describe('M3TableEmptyRow', () => {
  it('renders the message inside a table row with the given colSpan', () => {
    render(
      <table>
        <tbody>
          <M3TableEmptyRow colSpan={5} message="No guests found" />
        </tbody>
      </table>,
    );
    const cell = screen.getByText('No guests found');
    expect(cell.tagName).toBe('TD');
    expect(cell).toHaveAttribute('colSpan', '5');
  });
});
