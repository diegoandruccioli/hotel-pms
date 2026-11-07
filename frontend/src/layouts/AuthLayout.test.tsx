import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { AuthLayout } from './AuthLayout';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
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
    // The name appears in the brand panel (wide screens) and in the compact header (narrow screens).
    expect(screen.getAllByText('Hotel PMS').length).toBeGreaterThan(0);
    expect(screen.getByText('property_management_system')).toBeInTheDocument();
    expect(screen.getByText('Login Form')).toBeInTheDocument();
  });

  it('shows the brand panel with its headline and three feature lines', () => {
    renderLayout();
    expect(screen.getByText('brand_headline')).toBeInTheDocument();
    expect(screen.getByText('brand_lead')).toBeInTheDocument();
    for (const key of ['brand_feature_1', 'brand_feature_2', 'brand_feature_3']) {
      expect(screen.getByText(key)).toBeInTheDocument();
    }
    expect(screen.getByText('brand_footer')).toBeInTheDocument();
  });

  it('offers no links besides the skip link (no legal pages exist yet)', () => {
    renderLayout();
    const links = screen.getAllByRole('link');
    expect(links).toHaveLength(1);
    expect(links[0]).toHaveAttribute('href', '#main-content');
  });

  it('offers the language, contrast and text-size preferences before signing in', () => {
    renderLayout();
    expect(screen.getByRole('group', { name: 'prefs_label' })).toBeInTheDocument();
  });

  it('keeps the routed page and the preferences inside the main landmark', () => {
    renderLayout();
    const main = screen.getByRole('main');
    expect(main).toContainElement(screen.getByText('Login Form'));
    expect(main).toContainElement(screen.getByRole('group', { name: 'prefs_label' }));
  });

  it('has no accessibility violations', async () => {
    const { container } = renderLayout();
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
