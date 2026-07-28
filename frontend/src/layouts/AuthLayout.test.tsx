import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { AuthLayout } from './AuthLayout';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const ROOT_ENTRY = ['/login'];

const renderLayout = () =>
  render(
    <MemoryRouter initialEntries={ROOT_ENTRY}>
      <Routes>
        <Route element={<AuthLayout />}>
          <Route path="/login" element={<div>Login Form</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );

describe('AuthLayout', () => {
  it('renders the skip-link, branding, and the routed page content', () => {
    renderLayout();
    expect(screen.getByText('skip_to_main')).toHaveAttribute('href', '#main-content');
    expect(screen.getByText('Hotel PMS')).toBeInTheDocument();
    expect(screen.getByText('property_management_system')).toBeInTheDocument();
    expect(screen.getByText('Login Form')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = renderLayout();
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
