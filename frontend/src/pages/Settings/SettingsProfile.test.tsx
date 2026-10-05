import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { MemoryRouter } from 'react-router-dom';
import { SettingsProfile } from './SettingsProfile';
import { useAuthStore } from '../../store';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../store/authStore', () => ({
  useAuthStore: vi.fn(),
}));

const renderPage = () => render(<MemoryRouter><SettingsProfile /></MemoryRouter>);

describe('SettingsProfile', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAuthStore).mockReturnValue({ user: { username: 'admin', role: 'ADMIN' } } as never);
  });

  it('renders username and role', () => {
    renderPage();
    expect(screen.getByText('admin')).toBeInTheDocument();
    expect(screen.getByText('role_admin')).toBeInTheDocument();
  });

  it('renders the avatar initial', () => {
    renderPage();
    expect(screen.getByText('A')).toBeInTheDocument();
  });

  it('has no back button of its own: the settings layout provides the navigation', () => {
    renderPage();
    expect(screen.queryByRole('button', { name: 'back' })).not.toBeInTheDocument();
  });

  it('should have no accessibility violations', async () => {
    const { container } = renderPage();
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
