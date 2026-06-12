import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { MemoryRouter } from 'react-router-dom';
import { Profile } from './Profile';
import { authService } from '../services/authService';
import type { UserPayload } from '../types/auth.types'; // used in mockUser type annotation

const mockNavigate = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/authService', () => ({
  authService: { changePassword: vi.fn() },
}));

const addToastMock = vi.fn();
vi.mock('../store/toastStore', () => ({
  useToastStore: () => ({ addToast: addToastMock }),
}));

const logoutMock = vi.fn();
const mockUser: UserPayload = { sub: 'admin', username: 'admin', role: 'ADMIN' };

vi.mock('../store/authStore', () => ({
  useAuthStore: () => ({ user: mockUser, logout: logoutMock }),
}));

const renderProfile = () =>
  render(
    <MemoryRouter>
      <Profile />
    </MemoryRouter>
  );

describe('Profile', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders username and role', () => {
    renderProfile();
    expect(screen.getByText('admin')).toBeInTheDocument();
    expect(screen.getByText('role_admin')).toBeInTheDocument();
  });

  it('renders section headings', () => {
    renderProfile();
    expect(screen.getByText('section_account_info')).toBeInTheDocument();
    expect(screen.getByText('section_change_password')).toBeInTheDocument();
  });

  it('renders avatar initial', () => {
    renderProfile();
    expect(screen.getByText('A')).toBeInTheDocument();
  });

  it('navigates back when back button clicked', () => {
    renderProfile();
    fireEvent.click(screen.getByRole('button', { name: 'back' }));
    expect(mockNavigate).toHaveBeenCalledWith(-1);
  });

  it('submit button disabled when fields empty', () => {
    renderProfile();
    expect(screen.getByRole('button', { name: 'change_password' })).toBeDisabled();
  });

  it('shows validation error when passwords do not match', async () => {
    renderProfile();
    fireEvent.change(screen.getByLabelText('current_password'), { target: { value: 'oldPass1' } });
    fireEvent.change(screen.getByLabelText('new_password'), { target: { value: 'newPass1' } });
    fireEvent.change(screen.getByLabelText('confirm_new_password'), { target: { value: 'different' } });
    fireEvent.click(screen.getByRole('button', { name: 'change_password' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('passwords_dont_match');
    expect(authService.changePassword).not.toHaveBeenCalled();
  });

  it('shows validation error when new password too short', async () => {
    renderProfile();
    fireEvent.change(screen.getByLabelText('current_password'), { target: { value: 'oldPass1' } });
    fireEvent.change(screen.getByLabelText('new_password'), { target: { value: 'short' } });
    fireEvent.change(screen.getByLabelText('confirm_new_password'), { target: { value: 'short' } });
    fireEvent.click(screen.getByRole('button', { name: 'change_password' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('password_too_short');
    expect(authService.changePassword).not.toHaveBeenCalled();
  });

  it('calls changePassword and logs out on success', async () => {
    vi.mocked(authService.changePassword).mockResolvedValueOnce(undefined);
    renderProfile();
    fireEvent.change(screen.getByLabelText('current_password'), { target: { value: 'oldPass1' } });
    fireEvent.change(screen.getByLabelText('new_password'), { target: { value: 'HotelPms@@2026xx' } });
    fireEvent.change(screen.getByLabelText('confirm_new_password'), { target: { value: 'HotelPms@@2026xx' } });
    fireEvent.click(screen.getByRole('button', { name: 'change_password' }));
    await waitFor(() => expect(authService.changePassword).toHaveBeenCalledWith({
      currentPassword: 'oldPass1',
      newPassword: 'HotelPms@@2026xx',
    }));
    expect(addToastMock).toHaveBeenCalledWith('password_changed_success', 'success');
    expect(logoutMock).toHaveBeenCalled();
    expect(mockNavigate).toHaveBeenCalledWith('/login');
  });

  it('shows API error message on failure', async () => {
    vi.mocked(authService.changePassword).mockRejectedValueOnce({
      response: { data: { detail: 'Wrong current password' } },
    });
    renderProfile();
    fireEvent.change(screen.getByLabelText('current_password'), { target: { value: 'wrong' } });
    fireEvent.change(screen.getByLabelText('new_password'), { target: { value: 'HotelPms@@2026xx' } });
    fireEvent.change(screen.getByLabelText('confirm_new_password'), { target: { value: 'HotelPms@@2026xx' } });
    fireEvent.click(screen.getByRole('button', { name: 'change_password' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Wrong current password');
  });

  it('shows generic error when API response has no detail', async () => {
    vi.mocked(authService.changePassword).mockRejectedValueOnce(new Error('Network'));
    renderProfile();
    fireEvent.change(screen.getByLabelText('current_password'), { target: { value: 'oldPass' } });
    fireEvent.change(screen.getByLabelText('new_password'), { target: { value: 'HotelPms@@2026xx' } });
    fireEvent.change(screen.getByLabelText('confirm_new_password'), { target: { value: 'HotelPms@@2026xx' } });
    fireEvent.click(screen.getByRole('button', { name: 'change_password' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('password_change_failed');
  });

  it('should have no accessibility violations', async () => {
    const { container } = renderProfile();
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
