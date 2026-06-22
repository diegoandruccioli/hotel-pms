import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { MemoryRouter } from 'react-router-dom';
import { Settings } from './Settings';
import { useAuthStore } from '../store/authStore';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../store/authStore', () => ({
  useAuthStore: vi.fn(),
}));

const mockAuth = (role: string | undefined) => (selector: unknown) =>
  (selector as (s: { user: { role: string } | null }) => unknown)({ user: role ? { role } : null });

const renderSettings = () => render(<MemoryRouter><Settings /></MemoryRouter>);

describe('Settings hub', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the page heading', () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuth('RECEPTIONIST'));
    renderSettings();
    expect(screen.getByRole('heading', { level: 1, name: 'settings' })).toBeInTheDocument();
  });

  it('navigates back when the back button is clicked', () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuth('RECEPTIONIST'));
    renderSettings();
    fireEvent.click(screen.getByRole('button', { name: 'back' }));
    expect(mockNavigate).toHaveBeenCalledWith(-1);
  });

  it('shows the 4 standard categories for a non-admin role', () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuth('RECEPTIONIST'));
    renderSettings();
    expect(screen.getByRole('link', { name: /my_profile/ })).toHaveAttribute('href', '/settings/profile');
    expect(screen.getByRole('link', { name: /change_password/ })).toHaveAttribute('href', '/settings/password');
    expect(screen.getByRole('link', { name: /settings_section_accessibility/ })).toHaveAttribute('href', '/settings/accessibility');
    expect(screen.getByRole('link', { name: /settings_appearance_language_title/ })).toHaveAttribute('href', '/settings/appearance');
    expect(screen.queryByRole('link', { name: /settings_section_system/ })).not.toBeInTheDocument();
  });

  it('also shows the System category for ADMIN', () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuth('ADMIN'));
    renderSettings();
    expect(screen.getByRole('link', { name: /settings_section_system/ })).toHaveAttribute('href', '/settings/system');
  });

  it('also shows the System category for OWNER', () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuth('OWNER'));
    renderSettings();
    expect(screen.getByRole('link', { name: /settings_section_system/ })).toHaveAttribute('href', '/settings/system');
  });

  it('should have no accessibility violations', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuth('ADMIN'));
    const { container } = renderSettings();
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
