import type { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { axe } from 'vitest-axe';
import { CommandPalette } from './CommandPalette';
import { useAuthStore } from '../store/authStore';
import { guestService } from '../services/guestService';
import { reservationService } from '../services/reservationService';
import { renderWithQuery } from '../test-utils';
import type { UserPayload } from '../types';
import type { GuestResponseDTO } from '../types';
import type { ReservationResponse } from '../types';

const mockNavigate = vi.fn();

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (opts && typeof opts === 'object') {
        return Object.entries(opts).reduce(
          (s, [k, v]) => s.replace(`{{${k}}}`, String(v)),
          key,
        );
      }
      return key;
    },
  }),
}));

vi.mock('../store/authStore');
vi.mock('../services/guestService', () => ({
  guestService: { searchGuestsPaged: vi.fn() },
}));
vi.mock('../services/reservationService', () => ({
  reservationService: { searchReservations: vi.fn() },
}));

const RECEPTIONIST: UserPayload = { sub: '1', username: 'alice', role: 'RECEPTIONIST' };
const ADMIN: UserPayload = { sub: '2', username: 'bob', role: 'ADMIN' };

const page = <T,>(content: T[]) => ({ content, totalPages: 1, totalElements: content.length });

const GUEST: GuestResponseDTO = {
  id: 'g1', firstName: 'John', lastName: 'Doe', email: 'john@example.com', active: true,
  createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
};

const RESERVATION: ReservationResponse = {
  id: 'r1', guestId: 'g1', guestFullName: 'John Doe', checkInDate: '2026-09-01',
  checkOutDate: '2026-09-03', status: 'CONFIRMED', expectedGuests: 2, lineItems: [],
  active: true, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
  confirmationEmailFailed: false,
};

const mockAuthStore = (user: UserPayload | null) => {
  vi.mocked(useAuthStore).mockReturnValue(
    user?.role as unknown as ReturnType<typeof useAuthStore>,
  );
};

describe('CommandPalette', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthStore(RECEPTIONIST);
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValue(page([]) as never);
    vi.mocked(reservationService.searchReservations).mockResolvedValue(page([]) as never);
  });

  it('renders nothing when closed', () => {
    renderWithQuery(<CommandPalette open={false} onClose={vi.fn()} />);
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
  });

  it('navigates to the new reservation route via a quick action and closes', () => {
    const onClose = vi.fn();
    renderWithQuery(<CommandPalette open onClose={onClose} />);

    fireEvent.click(screen.getByText('palette_action_new_reservation'));

    expect(mockNavigate).toHaveBeenCalledWith('/reservations/new', undefined);
    expect(onClose).toHaveBeenCalled();
  });

  it('navigates to the walk-in route via a quick action', () => {
    const onClose = vi.fn();
    renderWithQuery(<CommandPalette open onClose={onClose} />);

    fireEvent.click(screen.getByText('palette_action_new_walkin'));

    expect(mockNavigate).toHaveBeenCalledWith('/stays/walk-in', undefined);
    expect(onClose).toHaveBeenCalled();
  });

  it('hides owner-only nav items for a RECEPTIONIST', () => {
    mockAuthStore(RECEPTIONIST);
    renderWithQuery(<CommandPalette open onClose={vi.fn()} />);
    expect(screen.queryByText('nav_owner_dashboard')).not.toBeInTheDocument();
  });

  it('shows owner-only nav items for an ADMIN', () => {
    mockAuthStore(ADMIN);
    renderWithQuery(<CommandPalette open onClose={vi.fn()} />);
    expect(screen.getByText('nav_owner_dashboard')).toBeInTheDocument();
  });

  it('navigates when a nav item is selected', () => {
    const onClose = vi.fn();
    renderWithQuery(<CommandPalette open onClose={onClose} />);

    fireEvent.click(screen.getByText('nav_billing'));

    expect(mockNavigate).toHaveBeenCalledWith('/billing', undefined);
    expect(onClose).toHaveBeenCalled();
  });

  it('shows matching guests after the debounced search resolves and navigates on select', async () => {
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValue(page([GUEST]) as never);
    const onClose = vi.fn();
    renderWithQuery(<CommandPalette open onClose={onClose} />);

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'John' } });

    await waitFor(() => expect(guestService.searchGuestsPaged).toHaveBeenCalledWith('John', 0, 5), {
      timeout: 1000,
    });
    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());

    fireEvent.click(screen.getByText('John Doe'));
    expect(mockNavigate).toHaveBeenCalledWith(`/guests?search=${encodeURIComponent('John Doe')}`, undefined);
    expect(onClose).toHaveBeenCalled();
  });

  it('shows matching reservations after the debounced search resolves and navigates on select', async () => {
    vi.mocked(reservationService.searchReservations).mockResolvedValue(page([RESERVATION]) as never);
    const onClose = vi.fn();
    renderWithQuery(<CommandPalette open onClose={onClose} />);

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'John' } });

    await waitFor(
      () =>
        expect(reservationService.searchReservations).toHaveBeenCalledWith({
          query: 'John', page: 0, size: 5, sort: 'checkInDate,desc',
        }),
      { timeout: 1000 },
    );
    // The mocked react-i18next `t()` (matching this repo's other tests) doesn't
    // resolve real message templates, so it returns the bare key here.
    await waitFor(() => expect(screen.getByText('palette_reservation_result')).toBeInTheDocument());

    fireEvent.click(screen.getByText('palette_reservation_result'));
    expect(mockNavigate).toHaveBeenCalledWith('/reservations/r1', undefined);
    expect(onClose).toHaveBeenCalled();
  });

  it('does not search below the minimum query length', async () => {
    renderWithQuery(<CommandPalette open onClose={vi.fn()} />);

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'j' } });

    await act(() => new Promise((resolve) => setTimeout(resolve, 350)));
    expect(guestService.searchGuestsPaged).not.toHaveBeenCalled();
  });

  it('resets the search box when reopened after being closed', () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
    const { rerender } = render(<CommandPalette open onClose={vi.fn()} />, { wrapper });
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'John' } });
    expect(screen.getByRole('combobox')).toHaveValue('John');

    rerender(<CommandPalette open={false} onClose={vi.fn()} />);
    rerender(<CommandPalette open onClose={vi.fn()} />);

    expect(screen.getByRole('combobox')).toHaveValue('');
  });

  it('has no accessibility violations while open', async () => {
    const { container } = renderWithQuery(<CommandPalette open onClose={vi.fn()} />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
