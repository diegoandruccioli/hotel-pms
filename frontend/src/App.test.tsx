import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { renderWithQuery as render } from './test-utils';
import App from './App';
import { useAuthStore } from './store';
import { authService } from './services';
import type { UserPayload } from './types/auth.types';

const { mockT } = vi.hoisted(() => ({ mockT: (key: string) => key }));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: mockT, i18n: { language: 'en', changeLanguage: vi.fn() } }),
  withTranslation: () => (WrappedComponent: React.ComponentType<{ t: (key: string) => string }>) => {
    const Wrapped = (props: object) => <WrappedComponent {...props} t={mockT} />;
    Wrapped.displayName = 'WithTranslation';
    return Wrapped;
  },
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('./store/authStore');
vi.mock('./services/authService');

interface MockAuthState {
  isAuthenticated: boolean;
  isLoading: boolean;
  user: UserPayload | null;
  checkAuth: (user: UserPayload | null) => void;
}

const mockStore = (state: MockAuthState) => {
  vi.mocked(useAuthStore).mockImplementation((selector?: unknown) =>
    typeof selector === 'function'
      ? (selector as (s: MockAuthState) => unknown)(state)
      : state,
  );
};

const ADMIN: UserPayload = { sub: '1', username: 'admin', role: 'ADMIN' };

describe('App', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.history.pushState({}, '', '/');
  });

  it('shows a loading spinner while the session check is in flight', () => {
    vi.mocked(authService.fetchMe).mockReturnValue(new Promise(() => { /* never resolves */ }));
    mockStore({ isAuthenticated: false, isLoading: true, user: null, checkAuth: vi.fn() });

    render(<App />);
    expect(screen.getByText('loading_session')).toBeInTheDocument();
  });

  it('calls authService.fetchMe on mount to resolve the session', () => {
    vi.mocked(authService.fetchMe).mockResolvedValue(ADMIN);
    mockStore({ isAuthenticated: false, isLoading: true, user: null, checkAuth: vi.fn() });

    render(<App />);
    expect(authService.fetchMe).toHaveBeenCalledOnce();
  });

  it('redirects an unauthenticated visitor to /login', async () => {
    vi.mocked(authService.fetchMe).mockResolvedValue(null as unknown as UserPayload);
    mockStore({ isAuthenticated: false, isLoading: false, user: null, checkAuth: vi.fn() });

    render(<App />);
    await waitFor(() => expect(screen.getByTestId('login-submit')).toBeInTheDocument());
    expect(window.location.pathname).toBe('/login');
  });

  it('does not redirect an authenticated visitor away from /', async () => {
    vi.mocked(authService.fetchMe).mockResolvedValue(ADMIN);
    mockStore({ isAuthenticated: true, isLoading: false, user: ADMIN, checkAuth: vi.fn() });

    render(<App />);
    await waitFor(() => expect(screen.getByText('skip_to_main')).toBeInTheDocument());
    expect(window.location.pathname).toBe('/');
  });

  it('loading state has no accessibility violations', async () => {
    vi.mocked(authService.fetchMe).mockReturnValue(new Promise(() => { /* never resolves */ }));
    mockStore({ isAuthenticated: false, isLoading: true, user: null, checkAuth: vi.fn() });

    const { container } = render(<App />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
