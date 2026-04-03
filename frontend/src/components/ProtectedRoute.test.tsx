/* eslint-disable react-perf/jsx-no-new-array-as-prop */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ProtectedRoute } from './ProtectedRoute';
import { useAuthStore } from '../store/authStore';

vi.mock('../store/authStore');

describe('ProtectedRoute', () => {
  it('should render outlet when authenticated', () => {
    vi.mocked(useAuthStore).mockImplementation((selector: unknown) =>
      (selector as (state: { isAuthenticated: boolean }) => boolean)({ isAuthenticated: true })
    );

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
    vi.mocked(useAuthStore).mockImplementation((selector: unknown) =>
      (selector as (state: { isAuthenticated: boolean }) => boolean)({ isAuthenticated: false })
    );

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
});
