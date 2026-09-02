import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { MainLayout } from './MainLayout';
import { useAuthStore } from '../store/authStore';
import { renderWithQuery } from '../test-utils';
import type { UserPayload } from '../types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (opts && typeof opts === 'object') {
        return Object.entries(opts).reduce(
          (s, [k, v]) => s.replace(`{{${k}}}`, String(v)),
          key,
        );
      }
      return key;
    },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

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
