import { describe, it, expect, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { Breadcrumbs } from './Breadcrumbs';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

type Crumbs = Parameters<typeof Breadcrumbs>[0]['crumbs'];

const ui = (crumbs: Crumbs) => (
  <MemoryRouter>
    <Breadcrumbs crumbs={crumbs} />
  </MemoryRouter>
);

const TRAIL = [
  { label: 'Front office' },
  { label: 'Reservations', to: '/reservations' },
  { label: 'New reservation', to: '/reservations/new' },
];

describe('Breadcrumbs', () => {
  it('renders nothing for fewer than two crumbs', () => {
    const { container, rerender } = render(ui([]));
    expect(container).toBeEmptyDOMElement();
    rerender(ui([{ label: 'Settings', to: '/settings' }]));
    expect(container).toBeEmptyDOMElement();
  });

  it('renders a labelled nav landmark with an ordered list', () => {
    render(ui(TRAIL));
    const nav = screen.getByRole('navigation', { name: 'breadcrumb_label' });
    expect(within(nav).getAllByRole('listitem')).toHaveLength(3);
  });

  it('links intermediate crumbs, leaves unlinked crumbs as text', () => {
    render(ui(TRAIL));
    expect(screen.getByRole('link', { name: 'Reservations' })).toHaveAttribute('href', '/reservations');
    expect(screen.queryByRole('link', { name: 'Front office' })).not.toBeInTheDocument();
    expect(screen.getByText('Front office')).toBeInTheDocument();
  });

  it('marks the last crumb as the current page and does not link it', () => {
    render(ui(TRAIL));
    expect(screen.getByText('New reservation')).toHaveAttribute('aria-current', 'page');
    expect(screen.queryByRole('link', { name: 'New reservation' })).not.toBeInTheDocument();
  });

  it('hides the decorative separators from assistive tech', () => {
    const { container } = render(ui(TRAIL));
    expect(container.querySelectorAll('li [aria-hidden="true"]')).toHaveLength(2);
  });

  it('hides the whole nav below sm when only a group heading and the current page would remain', () => {
    render(ui([{ label: 'Front office' }, { label: 'Guests', to: '/guests' }]));
    expect(screen.getByRole('navigation', { name: 'breadcrumb_label', hidden: true })).toHaveClass('max-sm:hidden');
  });

  it('keeps the nav on mobile when a linked parent remains, hiding only the group heading and its separator', () => {
    const { container } = render(ui(TRAIL));
    expect(screen.getByRole('navigation', { name: 'breadcrumb_label' })).not.toHaveClass('max-sm:hidden');
    const items = container.querySelectorAll('li');
    expect(items[0]).toHaveClass('max-sm:hidden');
    expect(items[1].querySelector('[aria-hidden="true"]')).toHaveClass('max-sm:hidden');
    expect(items[2].querySelector('[aria-hidden="true"]')).not.toHaveClass('max-sm:hidden');
  });

  it('keeps a linked first crumb visible on mobile (settings trail)', () => {
    const { container } = render(ui([{ label: 'Settings', to: '/settings' }, { label: 'Profile', to: '/settings/profile' }]));
    expect(container.querySelector('li')).not.toHaveClass('max-sm:hidden');
  });

  it('has no accessibility violations on a heading-and-page trail', async () => {
    const { container } = render(ui([{ label: 'Front office' }, { label: 'Guests', to: '/guests' }]));
    expect(await axe(container)).toHaveNoViolations();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(ui(TRAIL));
    expect(await axe(container)).toHaveNoViolations();
  });
});
