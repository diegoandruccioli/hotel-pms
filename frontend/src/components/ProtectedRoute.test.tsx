/* eslint-disable react-perf/jsx-no-new-array-as-prop */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';
import { ProtectedRoute } from './ProtectedRoute';
import { useAuthStore } from '../store';
import type { UserPayload } from '../types';

vi.mock('../store/authStore');

interface MockAuthState {
  isAuthenticated: boolean;
  user: UserPayload | null;
}

const mockStore = (state: MockAuthState) => {
  vi.mocked(useAuthStore).mockImplementation((selector: unknown) =>
    (selector as (s: MockAuthState) => unknown)(state)
  );
};

const adminUser: UserPayload = { sub: '1', username: 'admin', role: 'ADMIN' };
const ownerUser: UserPayload = { sub: '2', username: 'owner', role: 'OWNER' };
const receptionistUser: UserPayload = { sub: '3', username: 'receptionist', role: 'RECEPTIONIST' };

const LoginProbe = () => {
  const state = useLocation().state as { from?: string } | null;
  return <div>Login from {state?.from ?? 'nowhere'}</div>;
};

describe('ProtectedRoute', () => {
  it('should hand the requested page (path and query) to the login so it can send the visitor back', () => {
    mockStore({ isAuthenticated: false, user: null });

    render(
      <MemoryRouter initialEntries={['/reservations/groups/g1?tab=rooms']}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/reservations/groups/:id" element={<div>Group</div>} />
          </Route>
          <Route path="/login" element={<LoginProbe />} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Login from /reservations/groups/g1?tab=rooms')).toBeInTheDocument();
  });

  it('should render outlet when authenticated', () => {
    mockStore({ isAuthenticated: true, user: adminUser });

    render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/protected" element={<div>Protected Content</div>} />
          </Route>
          <Route path="/login" element={<div>Login Page</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });

  it('should redirect to /login when not authenticated', () => {
    mockStore({ isAuthenticated: false, user: null });

    render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/protected" element={<div>Protected Content</div>} />
          </Route>
          <Route path="/login" element={<div>Login Page</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Login Page')).toBeInTheDocument();
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  it('should render outlet when role is in allowedRoles (ADMIN)', () => {
    mockStore({ isAuthenticated: true, user: adminUser });

    render(
      <MemoryRouter initialEntries={['/owner-dashboard']}>
        <Routes>
          <Route element={<ProtectedRoute allowedRoles={['ADMIN', 'OWNER']} />}>
            <Route path="/owner-dashboard" element={<div>Owner Dashboard</div>} />
          </Route>
          <Route path="/" element={<div>Home</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Owner Dashboard')).toBeInTheDocument();
    expect(screen.queryByText('Home')).not.toBeInTheDocument();
  });

  it('should render outlet when role is in allowedRoles (OWNER)', () => {
    mockStore({ isAuthenticated: true, user: ownerUser });

    render(
      <MemoryRouter initialEntries={['/owner-dashboard']}>
        <Routes>
          <Route element={<ProtectedRoute allowedRoles={['ADMIN', 'OWNER']} />}>
            <Route path="/owner-dashboard" element={<div>Owner Dashboard</div>} />
          </Route>
          <Route path="/" element={<div>Home</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Owner Dashboard')).toBeInTheDocument();
    expect(screen.queryByText('Home')).not.toBeInTheDocument();
  });

  it('should redirect to / when authenticated but role not in allowedRoles', () => {
    mockStore({ isAuthenticated: true, user: receptionistUser });

    render(
      <MemoryRouter initialEntries={['/owner-dashboard']}>
        <Routes>
          <Route element={<ProtectedRoute allowedRoles={['ADMIN', 'OWNER']} />}>
            <Route path="/owner-dashboard" element={<div>Owner Dashboard</div>} />
          </Route>
          <Route path="/" element={<div>Home</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Home')).toBeInTheDocument();
    expect(screen.queryByText('Owner Dashboard')).not.toBeInTheDocument();
  });

  it('should redirect to / when authenticated but user is null with allowedRoles set', () => {
    mockStore({ isAuthenticated: true, user: null });

    render(
      <MemoryRouter initialEntries={['/owner-dashboard']}>
        <Routes>
          <Route element={<ProtectedRoute allowedRoles={['ADMIN', 'OWNER']} />}>
            <Route path="/owner-dashboard" element={<div>Owner Dashboard</div>} />
          </Route>
          <Route path="/" element={<div>Home</div>} />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText('Home')).toBeInTheDocument();
    expect(screen.queryByText('Owner Dashboard')).not.toBeInTheDocument();
  });

  describe('forced password change', () => {
    const mustChangeUser: UserPayload = { ...adminUser, mustChangePassword: true };

    const FromProbe = () => {
      const state = useLocation().state as { from?: string } | null;
      return <div>Change password, then {state?.from ?? 'nowhere'}</div>;
    };

    const renderAt = (entry: string, allowedRoles?: UserPayload['role'][]) =>
      render(
        <MemoryRouter initialEntries={[entry]}>
          <Routes>
            <Route element={<ProtectedRoute />}>
              <Route path="/reservations" element={<div>Reservations</div>} />
              <Route path="/settings/password" element={<FromProbe />} />
            </Route>
            <Route element={<ProtectedRoute allowedRoles={allowedRoles} />}>
              <Route path="/owner-dashboard" element={<div>Owner Dashboard</div>} />
            </Route>
            <Route path="/login" element={<div>Login Page</div>} />
          </Routes>
        </MemoryRouter>
      );

    it('sends a user who must change the password to the change-password page, remembering the requested one', () => {
      mockStore({ isAuthenticated: true, user: mustChangeUser });
      renderAt('/reservations?status=PENDING');

      expect(screen.getByText('Change password, then /reservations?status=PENDING')).toBeInTheDocument();
      expect(screen.queryByText('Reservations')).not.toBeInTheDocument();
    });

    it('lets the change-password page itself render', () => {
      mockStore({ isAuthenticated: true, user: mustChangeUser });
      renderAt('/settings/password');

      expect(screen.getByText('Change password, then nowhere')).toBeInTheDocument();
    });

    it('asks for the new password before any role check', () => {
      mockStore({ isAuthenticated: true, user: { ...receptionistUser, mustChangePassword: true } });
      renderAt('/owner-dashboard', ['ADMIN', 'OWNER']);

      expect(screen.getByText(/^Change password, then/)).toBeInTheDocument();
    });

    it('does not interfere when the flag is false or absent', () => {
      mockStore({ isAuthenticated: true, user: { ...adminUser, mustChangePassword: false } });
      renderAt('/reservations');

      expect(screen.getByText('Reservations')).toBeInTheDocument();
    });
  });

  it('should have no accessibility violations when authenticated', async () => {
    mockStore({ isAuthenticated: true, user: adminUser });
    const { container } = render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/protected" element={<main><h1>Protected Content</h1></main>} />
          </Route>
          <Route path="/login" element={<div>Login</div>} />
        </Routes>
      </MemoryRouter>
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
