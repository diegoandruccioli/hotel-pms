import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Login } from './Login';
import { BrowserRouter } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { authService } from '../services';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('../services/authService', () => ({
  authService: {
    login: vi.fn(),
    fetchMe: vi.fn(),
  },
}));

// Mock react-i18next
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

describe('Login Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders login form correctly', () => {
    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>
    );
    expect(screen.getByTestId('login-form')).toBeInTheDocument();
    expect(screen.getByLabelText('username')).toBeInTheDocument();
    expect(screen.getByLabelText('password')).toBeInTheDocument();
    expect(screen.getByTestId('login-submit')).toBeInTheDocument();
  });

  it('titles the page with a level-1 heading and a subtitle', () => {
    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>
    );
    expect(screen.getByRole('heading', { level: 1, name: 'login_title' })).toBeInTheDocument();
    expect(screen.getByText('login_subtitle')).toBeInTheDocument();
  });

  it('lets the user reveal and hide the password, keeping the field name', () => {
    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>
    );
    const field = screen.getByLabelText('password') as HTMLInputElement;
    expect(field.type).toBe('password');

    fireEvent.click(screen.getByRole('button', { name: 'show_password' }));
    expect(field.type).toBe('text');
    expect(screen.getByLabelText('password')).toBe(field);

    fireEvent.click(screen.getByRole('button', { name: 'hide_password' }));
    expect(field.type).toBe('password');
  });

  it('should have no accessibility violations', async () => {
    const { container } = render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });

  it('handles successful login', async () => {
    vi.mocked(authService.login).mockResolvedValueOnce({ mustChangePassword: false });
    vi.mocked(authService.fetchMe).mockResolvedValueOnce({
      sub: 'user',
      username: 'testuser',
      role: 'ADMIN',
    });

    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>
    );

    fireEvent.change(screen.getByLabelText('username'), { target: { value: 'testuser' } });
    fireEvent.change(screen.getByLabelText('password'), { target: { value: 'password123' } });
    fireEvent.click(screen.getByTestId('login-submit'));

    await waitFor(() => {
      expect(authService.login).toHaveBeenCalledWith({ username: 'testuser', password: 'password123' });
    });
    
    await waitFor(() => {
      expect(authService.fetchMe).toHaveBeenCalled();
    });
  });

  it('redirects to the change-password settings page when mustChangePassword is true', async () => {
    vi.mocked(authService.login).mockResolvedValueOnce({ mustChangePassword: true });
    vi.mocked(authService.fetchMe).mockResolvedValueOnce({
      sub: 'user',
      username: 'testuser',
      role: 'ADMIN',
    });

    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>
    );

    fireEvent.change(screen.getByLabelText('username'), { target: { value: 'testuser' } });
    fireEvent.change(screen.getByLabelText('password'), { target: { value: 'password123' } });
    fireEvent.click(screen.getByTestId('login-submit'));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/settings/password', { state: { mustChangePassword: true } });
    });
  });

  it('handles login failure (401)', async () => {
    vi.mocked(authService.login).mockRejectedValueOnce({
      response: { status: 401 }
    });

    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>
    );

    fireEvent.change(screen.getByLabelText('username'), { target: { value: 'invalid' } });
    fireEvent.change(screen.getByLabelText('password'), { target: { value: 'invalid' } });
    fireEvent.click(screen.getByTestId('login-submit'));

    const errorMessage = await screen.findByText('invalid_credentials');
    expect(errorMessage).toBeInTheDocument();
  });
});
