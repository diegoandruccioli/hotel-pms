import { describe, it, expect, vi } from 'vitest';
import { render as rtlRender, screen, fireEvent, type RenderOptions } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { ReactElement, ReactNode } from 'react';
import { axe } from 'vitest-axe';
import { PageHeader } from './PageHeader';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: { ns?: string }) => (opts?.ns ? `${opts.ns}:${key}` : key),
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const routerAt = (path: string) => {
  const entries = [path];
  return ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={entries}>{children}</MemoryRouter>
  );
};

const EXPLICIT_CRUMBS = [{ label: 'A', to: '/a' }, { label: 'B' }];
const NO_CRUMBS: never[] = [];

/** PageHeader reads the location to derive its breadcrumbs; default is the dashboard (no trail). */
const render = (ui: ReactElement, path = '/', options?: RenderOptions) =>
  rtlRender(ui, { wrapper: routerAt(path), ...options });

describe('PageHeader breadcrumbs', () => {
  it('derives the trail from the current route', () => {
    render(<PageHeader title="New" />, '/reservations/new');
    const nav = screen.getByRole('navigation', { name: 'breadcrumb_label' });
    expect(nav).toHaveTextContent('common:nav_reservations');
    expect(screen.getByRole('link', { name: 'common:nav_reservations' })).toHaveAttribute('href', '/reservations');
  });

  it('renders the trail before the title', () => {
    render(<PageHeader title="New" />, '/reservations/new');
    const nav = screen.getByRole('navigation', { name: 'breadcrumb_label' });
    const heading = screen.getByRole('heading', { level: 1 });
    expect(nav.compareDocumentPosition(heading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('uses crumbLabel for the last crumb', () => {
    render(<PageHeader title="Sala Rossi" crumbLabel="Sala Rossi" />, '/reservations/groups/g1');
    expect(screen.getByText('Sala Rossi', { selector: '[aria-current="page"]' })).toBeInTheDocument();
  });

  it('prefers explicit crumbs over the derived trail', () => {
    render(
      <PageHeader title="X" crumbs={EXPLICIT_CRUMBS} />,
      '/reservations/new',
    );
    expect(screen.getByRole('link', { name: 'A' })).toBeInTheDocument();
    expect(screen.queryByText('common:nav_reservations')).not.toBeInTheDocument();
  });

  it('crumbs={[]} hides the trail', () => {
    render(<PageHeader title="X" crumbs={NO_CRUMBS} />, '/reservations/new');
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('shows no trail on the dashboard', () => {
    render(<PageHeader title="Dashboard" />, '/');
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });
});

describe('PageHeader', () => {
  it('renders the title as an h1', () => {
    render(<PageHeader icon="group" title="Guests" />);
    expect(screen.getByRole('heading', { level: 1, name: 'Guests' })).toBeInTheDocument();
  });

  it('renders the subtitle when provided', () => {
    render(<PageHeader icon="group" title="Guests" subtitle="Manage guest profiles" />);
    expect(screen.getByText('Manage guest profiles')).toBeInTheDocument();
  });

  it('omits the subtitle paragraph when not provided', () => {
    render(<PageHeader icon="group" title="Guests" />);
    expect(screen.queryByText('Manage guest profiles')).not.toBeInTheDocument();
  });

  it('renders the actions node when provided', () => {
    render(<PageHeader icon="group" title="Guests" actions={<button type="button">Add guest</button>} />);
    expect(screen.getByRole('button', { name: 'Add guest' })).toBeInTheDocument();
  });

  it('renders the icon only when provided', () => {
    const { container, rerender } = render(<PageHeader icon="group" title="Guests" />);
    expect(container.querySelector('h1 span[aria-hidden="true"]')).toBeInTheDocument();
    rerender(<PageHeader title="Guests" />);
    expect(container.querySelector('h1 span[aria-hidden="true"]')).not.toBeInTheDocument();
  });

  it('shows no back button without onBack', () => {
    render(<PageHeader title="Guests" />);
    expect(screen.queryByRole('button', { name: 'back' })).not.toBeInTheDocument();
  });

  it('calls onBack when the back button is clicked', () => {
    const onBack = vi.fn();
    render(<PageHeader title="Guests" onBack={onBack} />);
    fireEvent.click(screen.getByRole('button', { name: 'back' }));
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it('renders the title adornment inside the h1', () => {
    render(<PageHeader title="Guests" titleAdornment={<span>Chip</span>} />);
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('GuestsChip');
  });

  it('forwards id and data-testid to the h1', () => {
    render(<PageHeader title="Guests" id="guests-title" titleTestId="guests-heading" />);
    const heading = screen.getByRole('heading', { level: 1 });
    expect(heading).toHaveAttribute('id', 'guests-title');
    expect(heading).toHaveAttribute('data-testid', 'guests-heading');
  });

  it('adds a bottom border only when bordered', () => {
    const { container, rerender } = render(<PageHeader title="Guests" />);
    expect(container.firstElementChild).not.toHaveClass('border-b');
    rerender(<PageHeader title="Guests" bordered />);
    expect(container.firstElementChild).toHaveClass('border-b');
  });

  it('appends a custom className to the wrapper', () => {
    const { container } = render(<PageHeader title="Guests" className="custom" />);
    expect(container.firstElementChild).toHaveClass('custom');
  });

  it('appends actionsClassName to the actions wrapper', () => {
    render(<PageHeader title="Guests" actions={<button type="button">Add</button>} actionsClassName="w-full" />);
    expect(screen.getByRole('button', { name: 'Add' }).parentElement).toHaveClass('w-full');
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <PageHeader
        icon="group"
        title="Guests"
        subtitle="Manage guest profiles"
        onBack={vi.fn()}
        bordered
        actions={<button type="button">Add guest</button>}
      />,
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
