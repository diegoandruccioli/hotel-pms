import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { Guests } from './Guests';

const mockUseSearchParams = vi.hoisted(() => vi.fn(() => [new URLSearchParams()] as [URLSearchParams]));
vi.mock('react-router-dom', () => ({
  useSearchParams: mockUseSearchParams,
}));
import { guestService } from '../services/guestService';
import { useAuthStore } from '../store/authStore';
import { useToastStore } from '../store/toastStore';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/guestService', () => ({
  guestService: { getAllGuests: vi.fn(), deleteGuest: vi.fn() },
}));

vi.mock('../store/authStore', () => ({
  useAuthStore: vi.fn(),
}));

vi.mock('../store/toastStore', () => ({
  useToastStore: vi.fn(),
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('./GuestFormModal', () => ({
  GuestFormModal: () => null,
}));

const GUEST = {
  id: 'guest-1',
  firstName: 'John',
  lastName: 'Doe',
  email: 'john@test.com',
  phone: '123',
  city: 'Rome',
  country: 'IT',
};

const mockAddToast = vi.fn();

const mockAuthAdmin = (selector: unknown) =>
  (selector as (s: { user: { role: string } }) => unknown)({ user: { role: 'ADMIN' } });

const mockAuthReceptionist = (selector: unknown) =>
  (selector as (s: { user: { role: string } }) => unknown)({ user: { role: 'RECEPTIONIST' } });

const mockAuthNull = (selector: unknown) =>
  (selector as (s: { user: null }) => unknown)({ user: null });

describe('Guests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAuthStore).mockImplementation(mockAuthNull);
    vi.mocked(useToastStore).mockReturnValue(mockAddToast);
  });

  it('should show loading spinner initially', () => {
    vi.mocked(guestService.getAllGuests).mockReturnValue(new Promise(() => {}));
    render(<Guests />);
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should render guest list on success', async () => {
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([GUEST] as never);

    render(<Guests />);

    await waitFor(() => {
      expect(screen.getByText('John Doe')).toBeInTheDocument();
    });
  });

  it('should show empty state message when no guests', async () => {
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([]);
    render(<Guests />);

    await waitFor(() => {
      expect(screen.getByText('no_guests_found')).toBeInTheDocument();
    });
  });

  it('should show error message on failure', async () => {
    vi.mocked(guestService.getAllGuests).mockRejectedValueOnce(new Error('Network error'));
    render(<Guests />);

    await waitFor(() => {
      expect(screen.getByText('error_loading_guests')).toBeInTheDocument();
    });
  });

  it('should render page title', async () => {
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([]);
    render(<Guests />);

    await waitFor(() => {
      expect(screen.getByText('nav_guests')).toBeInTheDocument();
    });
  });

  // T-FE-01: React JSX output encoding — XSS payloads in API data must be escaped as text
  it('should escape XSS payload in guest fields (T-FE-01)', async () => {
    const xssFirst = '<img src=x onerror=alert(1)>';
    const xssEmail = '"><svg onload=alert(2)>';
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([
      { id: '1', firstName: xssFirst, lastName: 'XSS', email: xssEmail, phone: '', city: '', country: '' },
    ] as never);

    const { container } = render(<Guests />);

    await waitFor(() => {
      // No injected elements: React must not have interpreted the payload as HTML
      expect(container.querySelector('img[onerror]')).toBeNull();
      expect(container.querySelector('svg[onload]')).toBeNull();
      // Payload is present as literal text content (HTML-escaped by React JSX)
      const cells = container.querySelectorAll('td');
      const nameCellText = cells[0]?.textContent ?? '';
      expect(nameCellText).toContain(xssFirst);
    });
  });

  it('should not show delete button for RECEPTIONIST', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthReceptionist);
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([GUEST] as never);
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: /delete John Doe/ })).not.toBeInTheDocument();
  });

  it('should show delete button for ADMIN', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([GUEST] as never);
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /delete John Doe/ })).toBeInTheDocument();
  });

  it('should open confirmation dialog when delete button is clicked', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([GUEST] as never);
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /delete John Doe/ }));

    expect(screen.getByText('delete_guest_confirm')).toBeInTheDocument();
  });

  it('should call deleteGuest and remove guest on confirm', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([GUEST] as never);
    vi.mocked(guestService.deleteGuest).mockResolvedValueOnce(undefined);
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /delete John Doe/ }));
    fireEvent.click(screen.getAllByRole('button', { name: /^delete$/ })[0]);

    await waitFor(() => {
      expect(guestService.deleteGuest).toHaveBeenCalledWith('guest-1');
      expect(mockAddToast).toHaveBeenCalledWith('guest_deleted_success', 'success');
    });
  });

  it('should show GDPR hold toast on 451 response', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([GUEST] as never);
    vi.mocked(guestService.deleteGuest).mockRejectedValueOnce({ response: { status: 451 } });
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /delete John Doe/ }));
    fireEvent.click(screen.getAllByRole('button', { name: /^delete$/ })[0]);

    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith('delete_guest_gdpr_hold', 'error');
    });
  });

  it('should filter guests by name on search input', async () => {
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([
      GUEST,
      { id: 'g2', firstName: 'Jane', lastName: 'Smith', email: 'jane@test.com', phone: '', city: '', country: '' },
    ] as never);
    render(<Guests />);
    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());

    const input = screen.getByRole('searchbox');
    fireEvent.change(input, { target: { value: 'Jane' } });

    await waitFor(() => {
      expect(screen.queryByText('John Doe')).not.toBeInTheDocument();
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
    }, { timeout: 500 });
  });

  it('should filter guests by email on search input', async () => {
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([
      GUEST,
      { id: 'g2', firstName: 'Jane', lastName: 'Smith', email: 'jane@test.com', phone: '', city: '', country: '' },
    ] as never);
    render(<Guests />);
    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());

    const input = screen.getByRole('searchbox');
    fireEvent.change(input, { target: { value: 'jane@test' } });

    await waitFor(() => {
      expect(screen.queryByText('John Doe')).not.toBeInTheDocument();
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
    }, { timeout: 500 });
  });

  it('should pre-populate search from URL ?search param', async () => {
    mockUseSearchParams.mockReturnValueOnce([new URLSearchParams('search=Jane')] as [URLSearchParams]);
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([
      GUEST,
      { id: 'g2', firstName: 'Jane', lastName: 'Smith', email: 'jane@test.com', phone: '', city: '', country: '' },
    ] as never);
    render(<Guests />);

    await waitFor(() => {
      expect(screen.queryByText('John Doe')).not.toBeInTheDocument();
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
    });
  });

  it('should have no accessibility violations', async () => {
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([]);
    const { container } = render(<Guests />);
    await waitFor(() => expect(screen.getByText('no_guests_found')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
