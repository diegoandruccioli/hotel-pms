import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { Login } from './Login';
import { BrowserRouter } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { authService } from '../services/authService';

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
    vi.mocked(authService.login).mockResolvedValueOnce(undefined);
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
