import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { SidebarNav } from './SidebarNav';
import type { SidebarSection } from '../config/navigation';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: { ns?: string }) => (opts?.ns ? `${opts.ns}:${key}` : key),
  }),
}));

const SECTIONS: SidebarSection[] = [
  {
    group: null,
    entries: [{ id: 'dashboard', path: '/', icon: 'dashboard', labelKey: 'nav_dashboard', ns: 'common', sidebar: true }],
  },
  {
    group: 'front-office',
    entries: [
      { id: 'guests', path: '/guests', icon: 'group', labelKey: 'nav_guests', ns: 'common', sidebar: true, group: 'front-office' },
      { id: 'stays', path: '/stays', icon: 'hotel', labelKey: 'nav_stays', ns: 'common', sidebar: true, group: 'front-office' },
    ],
  },
  {
    group: 'admin',
    entries: [
      { id: 'admin-users', path: '/admin/users', icon: 'manage_accounts', labelKey: 'users_label', ns: 'settings', sidebar: true, group: 'admin' },
    ],
  },
];

const GUESTS_ENTRY = ['/guests'];

const renderNav = (onNavigate?: () => void) =>
  render(
    <MemoryRouter initialEntries={GUESTS_ENTRY}>
      <SidebarNav sections={SECTIONS} onNavigate={onNavigate} />
    </MemoryRouter>,
  );

describe('SidebarNav', () => {
  it('renders a labelled navigation landmark', () => {
    renderNav();
    expect(screen.getByRole('navigation', { name: 'nav_main' })).toBeInTheDocument();
  });

  it('puts the ungrouped entry first, without a heading, and labels each group', () => {
    renderNav();
    const links = screen.getAllByRole('link');
    expect(links[0]).toHaveTextContent('nav_dashboard');
    expect(screen.getAllByRole('group')).toHaveLength(2);
    const group = screen.getByRole('group', { name: 'nav_group_front_office' });
    expect(within(group).getAllByRole('link')).toHaveLength(2);
  });

  it('translates each label in its own namespace', () => {
    renderNav();
    expect(screen.getByRole('link', { name: /common:nav_guests/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /settings:users_label/ })).toBeInTheDocument();
  });

  it('gives each rendered instance its own heading ids', () => {
    const { container } = render(
      <MemoryRouter initialEntries={GUESTS_ENTRY}>
        <SidebarNav sections={SECTIONS} />
        <SidebarNav sections={SECTIONS} />
      </MemoryRouter>,
    );
    const ids = [...container.querySelectorAll('[id]')].map((el) => el.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('marks only the current route as active', () => {
    renderNav();
    expect(screen.getByRole('link', { name: /nav_guests/ })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: /nav_stays/ })).not.toHaveAttribute('aria-current');
    expect(screen.getByRole('link', { name: /nav_dashboard/ })).not.toHaveAttribute('aria-current');
  });

  it('calls onNavigate when a link is followed', () => {
    const onNavigate = vi.fn();
    renderNav(onNavigate);
    fireEvent.click(screen.getByRole('link', { name: /nav_stays/ }));
    expect(onNavigate).toHaveBeenCalledOnce();
  });

  it('should have no accessibility violations', async () => {
    const { container } = renderNav();
    expect(await axe(container)).toHaveNoViolations();
  });
});
