/* eslint-disable react-perf/jsx-no-new-array-as-prop -- test-only render helper, not the real perf-sensitive render path */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { SettingsLayout } from './SettingsLayout';
import { useAuthStore } from '../../store';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../store/authStore', () => ({
  useAuthStore: vi.fn(),
}));

const renderAt = (path: string) => {
  const initialEntries = [path];
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <Routes>
        <Route path="/settings" element={<SettingsLayout />}>
          <Route path="profile" element={<p>profile content</p>} />
          <Route path="password" element={<p>password content</p>} />
          <Route path="system" element={<p>system content</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
};

const withRole = (role: string) =>
  vi.mocked(useAuthStore).mockImplementation(((sel?: (s: unknown) => unknown) => {
    const state = { user: { username: 'u', role } };
    return sel ? sel(state) : state;
  }) as never);

describe('SettingsLayout', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    withRole('ADMIN');
  });

  it('shows the settings title once and the routed section next to the nav', () => {
    renderAt('/settings/profile');
    expect(screen.getByRole('heading', { level: 1, name: 'settings' })).toBeInTheDocument();
    expect(screen.getByText('profile content')).toBeInTheDocument();
  });

  it('lists every section for ADMIN as links in a named navigation', () => {
    renderAt('/settings/profile');
    const nav = screen.getByRole('navigation', { name: 'settings_nav_label' });
    expect(within(nav).getAllByRole('link')).toHaveLength(7);
    expect(within(nav).getByRole('link', { name: /my_profile/ })).toHaveAttribute('href', '/settings/profile');
    expect(within(nav).getByRole('link', { name: /settings_section_system/ })).toHaveAttribute('href', '/settings/system');
  });

  it('hides the admin sections from RECEPTIONIST', () => {
    withRole('RECEPTIONIST');
    renderAt('/settings/profile');
    const nav = screen.getByRole('navigation', { name: 'settings_nav_label' });
    expect(within(nav).getAllByRole('link')).toHaveLength(4);
    expect(within(nav).queryByRole('link', { name: /settings_section_system/ })).not.toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: /settings_section_city_tax/ })).not.toBeInTheDocument();
  });

  it('marks only the current section with aria-current', () => {
    renderAt('/settings/password');
    const nav = screen.getByRole('navigation', { name: 'settings_nav_label' });
    expect(within(nav).getByRole('link', { name: /change_password/ })).toHaveAttribute('aria-current', 'page');
    expect(within(nav).getByRole('link', { name: /my_profile/ })).not.toHaveAttribute('aria-current');
  });

  it('has no accessibility violations', async () => {
    const { container } = renderAt('/settings/profile');
    expect(await axe(container)).toHaveNoViolations();
  });
});
