import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { ListToolbar } from './ListToolbar';

const renderToolbar = (props: Partial<React.ComponentProps<typeof ListToolbar>> = {}) => {
  const onSearchChange = vi.fn();
  render(
    <ListToolbar searchLabel="Search" searchValue="" onSearchChange={onSearchChange} {...props} />,
  );
  return { onSearchChange };
};

describe('ListToolbar', () => {
  it('renders a named search field and reports typing', () => {
    const { onSearchChange } = renderToolbar();
    fireEvent.change(screen.getByRole('searchbox', { name: 'Search' }), { target: { value: 'rossi' } });
    expect(onSearchChange).toHaveBeenCalledTimes(1);
  });

  it('uses the label as placeholder by default', () => {
    renderToolbar();
    expect(screen.getByRole('searchbox', { name: 'Search' })).toHaveAttribute('placeholder', 'Search');
  });

  it('shows a shorter placeholder while the accessible name stays the full label', () => {
    renderToolbar({ searchLabel: 'Search by invoice number, guest name or email', searchPlaceholder: 'Invoice #, guest or email' });
    const field = screen.getByRole('searchbox', { name: 'Search by invoice number, guest name or email' });
    expect(field).toHaveAttribute('placeholder', 'Invoice #, guest or email');
  });

  it('renders chips and trailing actions when given', () => {
    renderToolbar({ children: <button type="button">Chip</button>, trailing: <button type="button">Export</button> });
    expect(screen.getByRole('button', { name: 'Chip' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Export' })).toBeInTheDocument();
  });

  it('names the chip row as a group when given a label', () => {
    renderToolbar({ filtersLabel: 'Filters', children: <button type="button">Chip</button> });
    expect(screen.getByRole('group', { name: 'Filters' })).toContainElement(screen.getByRole('button', { name: 'Chip' }));
  });

  it('renders only the search field without chips or actions', () => {
    renderToolbar();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <ListToolbar searchLabel="Search" searchValue="" onSearchChange={vi.fn()} trailing={<button type="button">Export</button>}>
        <button type="button">Chip</button>
      </ListToolbar>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
