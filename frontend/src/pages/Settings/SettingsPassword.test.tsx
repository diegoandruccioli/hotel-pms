import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { MemoryRouter } from 'react-router-dom';
import { SettingsPassword } from './SettingsPassword';
import { authService } from '../../services';

const mockNavigate = vi.fn();
const mockUseLocation = vi.fn(() => ({ state: null }));
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate, useLocation: () => mockUseLocation() };
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/authService', () => ({
  authService: { changePassword: vi.fn(), fetchMe: vi.fn() },
}));

const addToastMock = vi.fn();
vi.mock('../../store/toastStore', () => ({
  useToastStore: () => ({ addToast: addToastMock }),
}));

const checkAuthMock = vi.fn();
vi.mock('../../store/authStore', () => ({
  useAuthStore: () => ({ checkAuth: checkAuthMock }),
}));

const renderPage = () => render(<MemoryRouter><SettingsPassword /></MemoryRouter>);

describe('SettingsPassword', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseLocation.mockReturnValue({ state: null } as never);
  });

  it('shows the must-change-password banner when redirected with that state', () => {
    mockUseLocation.mockReturnValue({ state: { mustChangePassword: true } } as never);
    renderPage();
    expect(screen.getByText('must_change_password_banner')).toBeInTheDocument();
  });

  it('does not show the banner on a normal visit', () => {
    renderPage();
    expect(screen.queryByText('must_change_password_banner')).not.toBeInTheDocument();
  });

  it('navigates back in history when the back button is clicked', () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'back' }));
    expect(mockNavigate).toHaveBeenCalledWith(-1);
  });

  it('submit button disabled when fields empty', () => {
    renderPage();
    expect(screen.getByRole('button', { name: 'change_password' })).toBeDisabled();
  });

  it('shows validation error when passwords do not match', async () => {
    renderPage();
    fireEvent.change(screen.getByLabelText('current_password'), { target: { value: 'oldPass1' } });
    fireEvent.change(screen.getByLabelText('new_password'), { target: { value: 'newPass1' } });
    fireEvent.change(screen.getByLabelText('confirm_new_password'), { target: { value: 'different' } });
    fireEvent.click(screen.getByRole('button', { name: 'change_password' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('passwords_dont_match');
    expect(authService.changePassword).not.toHaveBeenCalled();
  });

  it('shows validation error when new password does not meet requirements', async () => {
    renderPage();
    fireEvent.change(screen.getByLabelText('current_password'), { target: { value: 'oldPass1' } });
    fireEvent.change(screen.getByLabelText('new_password'), { target: { value: 'short' } });
    fireEvent.change(screen.getByLabelText('confirm_new_password'), { target: { value: 'short' } });
    fireEvent.click(screen.getByRole('button', { name: 'change_password' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('password_requirements_not_met');
    expect(authService.changePassword).not.toHaveBeenCalled();
  });

  it('shows the live password requirements checklist next to the new password field', () => {
    renderPage();
    expect(screen.getByLabelText('password_requirements')).toBeInTheDocument();
  });

  it('BUG-13: keeps the session alive on success instead of forcing a re-login', async () => {
    vi.mocked(authService.changePassword).mockResolvedValueOnce(undefined);
    vi.mocked(authService.fetchMe).mockResolvedValueOnce({
      sub: 'u1', username: 'r4pwtest', role: 'RECEPTIONIST', mustChangePassword: false,
    });
    renderPage();
    fireEvent.change(screen.getByLabelText('current_password'), { target: { value: 'oldPass1' } });
    fireEvent.change(screen.getByLabelText('new_password'), { target: { value: 'HotelPms@@2026xx' } });
    fireEvent.change(screen.getByLabelText('confirm_new_password'), { target: { value: 'HotelPms@@2026xx' } });
    fireEvent.click(screen.getByRole('button', { name: 'change_password' }));
    await waitFor(() => expect(authService.changePassword).toHaveBeenCalledWith({
      currentPassword: 'oldPass1',
      newPassword: 'HotelPms@@2026xx',
    }));
    expect(addToastMock).toHaveBeenCalledWith('password_changed_success', 'success');
    // The backend already issued a fresh, valid token pair for this change —
    // no logout, just a session-store refresh so a stale mustChangePassword
    // flag (if this was the forced flow) is cleared.
    await waitFor(() => expect(checkAuthMock).toHaveBeenCalledWith(
      expect.objectContaining({ mustChangePassword: false }),
    ));
    expect(mockNavigate).toHaveBeenCalledWith(-1);
  });

  it('navigates to the dashboard (not back) when the change was the forced mustChangePassword flow', async () => {
    mockUseLocation.mockReturnValue({ state: { mustChangePassword: true } } as never);
    vi.mocked(authService.changePassword).mockResolvedValueOnce(undefined);
    vi.mocked(authService.fetchMe).mockResolvedValueOnce({
      sub: 'u1', username: 'r4pwtest', role: 'RECEPTIONIST', mustChangePassword: false,
    });
    renderPage();
    fireEvent.change(screen.getByLabelText('current_password'), { target: { value: 'oldPass1' } });
    fireEvent.change(screen.getByLabelText('new_password'), { target: { value: 'HotelPms@@2026xx' } });
    fireEvent.change(screen.getByLabelText('confirm_new_password'), { target: { value: 'HotelPms@@2026xx' } });
    fireEvent.click(screen.getByRole('button', { name: 'change_password' }));
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/'));
  });

  it('still completes the navigation if the post-change session refresh itself fails', async () => {
    vi.mocked(authService.changePassword).mockResolvedValueOnce(undefined);
    vi.mocked(authService.fetchMe).mockRejectedValueOnce(new Error('network blip'));
    renderPage();
    fireEvent.change(screen.getByLabelText('current_password'), { target: { value: 'oldPass1' } });
    fireEvent.change(screen.getByLabelText('new_password'), { target: { value: 'HotelPms@@2026xx' } });
    fireEvent.change(screen.getByLabelText('confirm_new_password'), { target: { value: 'HotelPms@@2026xx' } });
    fireEvent.click(screen.getByRole('button', { name: 'change_password' }));
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith(-1));
  });

  it('shows API error message on failure', async () => {
    vi.mocked(authService.changePassword).mockRejectedValueOnce({
      response: { data: { detail: 'Wrong current password' } },
    });
    renderPage();
    fireEvent.change(screen.getByLabelText('current_password'), { target: { value: 'wrong' } });
    fireEvent.change(screen.getByLabelText('new_password'), { target: { value: 'HotelPms@@2026xx' } });
    fireEvent.change(screen.getByLabelText('confirm_new_password'), { target: { value: 'HotelPms@@2026xx' } });
    fireEvent.click(screen.getByRole('button', { name: 'change_password' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Wrong current password');
  });

  it('shows generic error when API response has no detail', async () => {
    vi.mocked(authService.changePassword).mockRejectedValueOnce(new Error('Network'));
    renderPage();
    fireEvent.change(screen.getByLabelText('current_password'), { target: { value: 'oldPass' } });
    fireEvent.change(screen.getByLabelText('new_password'), { target: { value: 'HotelPms@@2026xx' } });
    fireEvent.change(screen.getByLabelText('confirm_new_password'), { target: { value: 'HotelPms@@2026xx' } });
    fireEvent.click(screen.getByRole('button', { name: 'change_password' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('password_change_failed');
  });

  it('should have no accessibility violations', async () => {
    const { container } = renderPage();
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
