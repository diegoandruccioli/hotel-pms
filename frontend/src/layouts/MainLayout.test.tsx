import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor, within, act } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { MainLayout } from './MainLayout';
import { useAuthStore, useSettingsStore } from '../store';
import { renderWithQuery } from '../test-utils';
import type { UserPayload } from '../types';

vi.mock('react-i18next', () => {
  // One stable `t`, like the real hook: RouteAnnouncer re-focuses <main> whenever `t` changes.
  const value = {
    t: (key: string, opts?: Record<string, unknown>) => {
      if (opts && typeof opts === 'object') {
        return Object.entries(opts).reduce(
          (s, [k, v]) => s.replace(`{{${k}}}`, String(v)),
          key,
        );
      }
      return key;
    },
  };
  return {
    useTranslation: () => value,
    initReactI18next: { type: '3rdParty', init: vi.fn() },
  };
});

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('../store/authStore');

const ROOT_ENTRY = ['/'];

const RECEPTIONIST: UserPayload = { sub: '1', username: 'alice', role: 'RECEPTIONIST' };
const ADMIN: UserPayload = { sub: '2', username: 'bob', role: 'ADMIN' };

const mockAuthStore = (user: UserPayload | null, logout = vi.fn()) => {
  vi.mocked(useAuthStore).mockReturnValue({ user, logout } as unknown as ReturnType<typeof useAuthStore>);
};

const renderLayout = () =>
  renderWithQuery(
    <MemoryRouter initialEntries={ROOT_ENTRY}>
      <Routes>
        <Route element={<MainLayout />}>
          <Route path="/" element={<div>Dashboard Content</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );

describe('MainLayout', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    useSettingsStore.setState({ sidebarCollapsed: false });
  });

  it('renders the skip-link, sidebar nav, and the routed page content', () => {
    mockAuthStore(RECEPTIONIST);
    renderLayout();

    expect(screen.getByText('skip_to_main')).toHaveAttribute('href', '#main-content');
    expect(screen.getAllByText('nav_dashboard').length).toBeGreaterThan(0);
    expect(screen.getAllByText('nav_billing').length).toBeGreaterThan(0);
    expect(screen.getByText('Dashboard Content')).toBeInTheDocument();
  });

  it('shows username and role for the logged-in user', () => {
    mockAuthStore(RECEPTIONIST);
    renderLayout();
    expect(screen.getByText('alice')).toBeInTheDocument();
  });

  it('hides the owner-only nav item for a RECEPTIONIST', () => {
    mockAuthStore(RECEPTIONIST);
    renderLayout();
    expect(screen.queryByText('nav_owner_dashboard')).not.toBeInTheDocument();
  });

  it('shows the owner-only nav item for an ADMIN', () => {
    mockAuthStore(ADMIN);
    renderLayout();
    expect(screen.getAllByText('nav_owner_dashboard').length).toBeGreaterThan(0);
  });

  it('shows the night-audit nav item for a RECEPTIONIST (GAP-26: night-shift front-desk work, not owner-only)', () => {
    mockAuthStore(RECEPTIONIST);
    renderLayout();
    expect(screen.getAllByText('nav_night_audit').length).toBeGreaterThan(0);
  });

  it('hides the night-audit nav item from a GUEST (route is closed to that role)', () => {
    mockAuthStore({ sub: '3', username: 'carol', role: 'GUEST' });
    renderLayout();
    expect(screen.queryByText('nav_night_audit')).not.toBeInTheDocument();
  });

  it('groups the sidebar entries under headings, the admin group for an ADMIN', () => {
    mockAuthStore(ADMIN);
    renderLayout();
    const sidebar = screen.getByRole('complementary');
    for (const heading of ['nav_group_front_office', 'nav_group_operations', 'nav_group_revenue', 'nav_group_admin']) {
      expect(within(sidebar).getByText(heading)).toBeInTheDocument();
    }
    expect(within(sidebar).getByRole('group', { name: 'nav_group_admin' })).toBeInTheDocument();
    expect(within(sidebar).getByText('settings_section_admin_users')).toBeInTheDocument();
  });

  it('hides the admin group from a RECEPTIONIST', () => {
    mockAuthStore(RECEPTIONIST);
    renderLayout();
    expect(screen.queryByText('nav_group_admin')).not.toBeInTheDocument();
    expect(screen.queryByText('settings_section_admin_users')).not.toBeInTheDocument();
  });

  it('renders the same groups inside the mobile drawer', () => {
    mockAuthStore(ADMIN);
    renderLayout();
    fireEvent.click(screen.getByLabelText('nav_menu'));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText('nav_group_front_office')).toBeInTheDocument();
    expect(within(dialog).getByText('nav_group_admin')).toBeInTheDocument();
  });

  it('toggles the sidebar between expanded and compact and remembers the choice', () => {
    mockAuthStore(ADMIN);
    const { container } = renderLayout();
    const aside = container.querySelector('#app-sidebar');
    const toggle = screen.getByRole('button', { name: 'sidebar_collapse' });
    const content = container.querySelector('#app-sidebar + div');
    expect(aside).toHaveClass('w-66');
    expect(content).toHaveClass('lg:ml-66');

    fireEvent.click(toggle);

    expect(aside).toHaveClass('w-20');
    expect(content).toHaveClass('lg:ml-20');
    expect(localStorage.getItem('hotel-pms-sidebar-collapsed')).toBe('true');

    fireEvent.click(screen.getByRole('button', { name: 'sidebar_expand' }));

    expect(aside).toHaveClass('w-66');
    expect(content).toHaveClass('lg:ml-66');
    expect(localStorage.getItem('hotel-pms-sidebar-collapsed')).toBe('false');
  });

  it('keeps keyboard focus on the sidebar toggle when it flips the sidebar', () => {
    mockAuthStore(ADMIN);
    renderLayout();
    const toggle = screen.getByRole('button', { name: 'sidebar_collapse' });
    toggle.focus();

    fireEvent.click(toggle);

    const after = screen.getByRole('button', { name: 'sidebar_expand' });
    expect(after).toBe(toggle);
    expect(after).toHaveFocus();
  });

  it('keeps the mobile drawer expanded while the desktop sidebar is compact', () => {
    useSettingsStore.setState({ sidebarCollapsed: true });
    mockAuthStore(ADMIN);
    renderLayout();
    fireEvent.click(screen.getByLabelText('nav_menu'));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText('nav_group_front_office')).not.toHaveClass('sr-only');
  });

  it('has no accessibility violations with the compact sidebar', async () => {
    useSettingsStore.setState({ sidebarCollapsed: true });
    mockAuthStore(ADMIN);
    const { container } = renderLayout();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('closes the drawer when the viewport grows past the lg breakpoint (its focus trap would hold a hidden dialog)', () => {
    const original = window.matchMedia;
    let onChange: ((e: { matches: boolean }) => void) | undefined;
    window.matchMedia = ((query: string) => ({
      matches: false,
      media: query,
      addEventListener: (_: string, cb: (e: { matches: boolean }) => void) => { onChange = cb; },
      removeEventListener: () => {},
    })) as unknown as typeof window.matchMedia;
    try {
      mockAuthStore(ADMIN);
      renderLayout();
      fireEvent.click(screen.getByLabelText('nav_menu'));
      expect(screen.getByRole('dialog')).toBeInTheDocument();

      act(() => onChange?.({ matches: false }));
      expect(screen.getByRole('dialog')).toBeInTheDocument();

      act(() => onChange?.({ matches: true }));
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    } finally {
      window.matchMedia = original;
    }
  });

  it('has no accessibility violations with the drawer open (heading ids stay unique)', async () => {
    mockAuthStore(ADMIN);
    const { container } = renderLayout();
    fireEvent.click(screen.getByLabelText('nav_menu'));
    expect(await axe(container)).toHaveNoViolations();
  });

  it('opens the mobile drawer from the hamburger button', () => {
    mockAuthStore(RECEPTIONIST);
    renderLayout();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('nav_menu'));
    expect(screen.getByRole('dialog')).toHaveAttribute('aria-modal', 'true');
  });

  it('closes the drawer on Escape (item 7c — the drawer had a focus trap but no Escape handler)', () => {
    mockAuthStore(RECEPTIONIST);
    renderLayout();
    fireEvent.click(screen.getByLabelText('nav_menu'));
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('closes the drawer when the scrim is clicked', () => {
    mockAuthStore(RECEPTIONIST);
    renderLayout();
    fireEvent.click(screen.getByLabelText('nav_menu'));
    const dialog = screen.getByRole('dialog');

    fireEvent.click(dialog.querySelector('[aria-hidden="true"]')!);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('closes the drawer after navigating via a drawer link', () => {
    mockAuthStore(RECEPTIONIST);
    renderLayout();
    fireEvent.click(screen.getByLabelText('nav_menu'));

    const dialog = screen.getByRole('dialog');
    fireEvent.click(within(dialog).getAllByText('nav_billing')[0]);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('logs out and navigates to /login via the user menu', async () => {
    const logout = vi.fn();
    mockAuthStore(RECEPTIONIST, logout);
    renderWithQuery(
      <MemoryRouter initialEntries={ROOT_ENTRY}>
        <Routes>
          <Route element={<MainLayout />}>
            <Route path="/" element={<div>Dashboard Content</div>} />
          </Route>
          <Route path="/login" element={<div>Login Page</div>} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: /user_menu_label/ }));
    fireEvent.click(screen.getByText('log_out'));

    expect(logout).toHaveBeenCalledOnce();
    await waitFor(() => expect(screen.getByText('Login Page')).toBeInTheDocument());
  });

  it('has no accessibility violations', async () => {
    mockAuthStore(RECEPTIONIST);
    const { container } = renderLayout();
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
