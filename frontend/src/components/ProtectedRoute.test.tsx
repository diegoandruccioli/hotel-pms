/* eslint-disable react-perf/jsx-no-new-array-as-prop */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ProtectedRoute } from './ProtectedRoute';
import { useAuthStore } from '../store/authStore';
import type { UserPayload } from '../types/auth.types';

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

describe('ProtectedRoute', () => {
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
});
