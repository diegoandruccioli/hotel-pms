import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { MemoryRouter } from 'react-router-dom';
import { Reservations } from './Reservations';
import { reservationService } from '../services/reservationService';
import { inventoryService } from '../services/inventoryService';
import { useAuthStore } from '../store/authStore';
import { useToastStore } from '../store/toastStore';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/reservationService', () => ({
  reservationService: { getAllReservations: vi.fn(), cancelReservation: vi.fn() },
}));

vi.mock('../services/inventoryService', () => ({
  inventoryService: { getAllRooms: vi.fn() },
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

const CONFIRMED_RESERVATION = {
  id: 'res-1',
  guestId: 'g1',
  guestFullName: 'John Doe',
  checkInDate: '2026-04-01',
  checkOutDate: '2026-04-03',
  status: 'CONFIRMED',
  active: true,
};

const CANCELLED_RESERVATION = {
  ...CONFIRMED_RESERVATION,
  id: 'res-2',
  status: 'CANCELLED',
};

const mockAddToast = vi.fn();

const mockAuthAdmin = (selector: unknown) =>
  (selector as (s: { user: { role: string } }) => unknown)({ user: { role: 'ADMIN' } });

const mockAuthNull = (selector: unknown) =>
  (selector as (s: { user: null }) => unknown)({ user: null });

describe('Reservations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue({ content: [], totalElements: 0 } as never);
    vi.mocked(useAuthStore).mockImplementation(mockAuthNull);
    vi.mocked(useToastStore).mockReturnValue(mockAddToast);
  });

  it('should show loading spinner initially', () => {
    vi.mocked(reservationService.getAllReservations).mockReturnValue(new Promise(() => {}));
    render(
      <MemoryRouter>
        <Reservations />
      </MemoryRouter>
    );
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should render reservations on success', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValueOnce([
      { id: '1', guestId: 'g1', guestFullName: 'John Doe', checkInDate: '2026-04-01', checkOutDate: '2026-04-03', status: 'CONFIRMED' },
    ] as never);

    render(
      <MemoryRouter>
        <Reservations />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('John Doe')).toBeInTheDocument();
    });
  });

  it('should show empty state message when no reservations', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValueOnce([]);
    render(
      <MemoryRouter>
        <Reservations />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('no_reservations_found')).toBeInTheDocument();
    });
  });

  it('should show error on failure', async () => {
    vi.mocked(reservationService.getAllReservations).mockRejectedValueOnce(new Error('Network error'));
    render(
      <MemoryRouter>
        <Reservations />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('error_loading_reservations')).toBeInTheDocument();
    });
  });

  it('should not show cancel button for non-admin users', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([CONFIRMED_RESERVATION] as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: /cancel_reservation res-1/ })).not.toBeInTheDocument();
  });

  it('should show cancel button for ADMIN on CONFIRMED reservation', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([CONFIRMED_RESERVATION] as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByRole('button', { name: /cancel_reservation res-1/ })).toBeInTheDocument());
  });

  it('should not show cancel button on CANCELLED reservation', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([CANCELLED_RESERVATION] as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: /cancel_reservation res-2/ })).not.toBeInTheDocument();
  });

  it('should open confirmation dialog when cancel button is clicked', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([CONFIRMED_RESERVATION] as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByRole('button', { name: /cancel_reservation res-1/ })).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /cancel_reservation res-1/ }));

    expect(screen.getByText('cancel_reservation_confirm')).toBeInTheDocument();
  });

  it('should call cancelReservation and show toast on confirm', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([CONFIRMED_RESERVATION] as never);
    vi.mocked(reservationService.cancelReservation).mockResolvedValueOnce({} as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByRole('button', { name: /cancel_reservation res-1/ })).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /cancel_reservation res-1/ }));
    fireEvent.click(screen.getAllByRole('button', { name: 'cancel_reservation' })[0]);

    await waitFor(() => {
      expect(reservationService.cancelReservation).toHaveBeenCalledWith('res-1');
      expect(mockAddToast).toHaveBeenCalledWith('reservation_cancelled_success', 'success');
    });
  });

  it('should have no accessibility violations', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValueOnce([]);
    const { container } = render(
      <MemoryRouter>
        <Reservations />
      </MemoryRouter>
    );
    await waitFor(() => expect(screen.getByText('no_reservations_found')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
