import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
/* eslint-disable react-perf/jsx-no-new-array-as-prop -- test-only render helper, not the real perf-sensitive render path */
import { MemoryRouter } from 'react-router-dom';

const mockNavigate = vi.hoisted(() => vi.fn());
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});
import { Reservations } from './Reservations';
import { reservationService } from '../services/reservationService';
import { inventoryService } from '../services/inventoryService';
import { useAuthStore } from '../store/authStore';
import { useToastStore } from '../store/toastStore';

vi.mock('react-i18next', () => {
  const t = (key: string) => key;
  return {
    useTranslation: () => ({ t, i18n: { language: 'en' } }),
    initReactI18next: { type: '3rdParty', init: vi.fn() },
  };
});

vi.mock('../services/reservationService', () => ({
  reservationService: {
    getAllReservations: vi.fn(),
    deleteReservation: vi.fn(),
    retryConfirmationEmail: vi.fn(),
  },
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

  it('should show confirmation-email-failed badge and retry, clearing the flag on success', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValueOnce([
      { ...CONFIRMED_RESERVATION, confirmationEmailFailed: true,
        confirmationEmailFailureReason: 'NOTIFICATION_SERVICE_UNAVAILABLE' },
    ] as never);
    vi.mocked(reservationService.retryConfirmationEmail).mockResolvedValueOnce({
      ...CONFIRMED_RESERVATION, confirmationEmailFailed: false, confirmationEmailFailureReason: null,
    } as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('confirmation_email_failed')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'retry_confirmation_email' }));

    await waitFor(() => {
      expect(reservationService.retryConfirmationEmail).toHaveBeenCalledWith('res-1');
      expect(screen.queryByText('confirmation_email_failed')).not.toBeInTheDocument();
    });
  });

  it('should keep the confirmation-email-failed badge when retry fails', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValueOnce([
      { ...CONFIRMED_RESERVATION, confirmationEmailFailed: true,
        confirmationEmailFailureReason: 'NOTIFICATION_SERVICE_UNAVAILABLE' },
    ] as never);
    vi.mocked(reservationService.retryConfirmationEmail).mockRejectedValueOnce(new Error('still down'));
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('confirmation_email_failed')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'retry_confirmation_email' }));

    await waitFor(() => {
      expect(reservationService.retryConfirmationEmail).toHaveBeenCalledWith('res-1');
      expect(screen.getByText('confirmation_email_failed')).toBeInTheDocument();
    });
  });

  it('should not show delete button for non-admin users', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([CONFIRMED_RESERVATION] as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: /delete_reservation res-1/ })).not.toBeInTheDocument();
  });

  it('should show delete button for ADMIN on CONFIRMED reservation', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([CONFIRMED_RESERVATION] as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByRole('button', { name: /delete_reservation res-1/ })).toBeInTheDocument());
  });

  it('should not show delete button on CANCELLED reservation', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([CANCELLED_RESERVATION] as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: /delete_reservation res-2/ })).not.toBeInTheDocument();
  });

  it('should open confirmation dialog when delete button is clicked', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([CONFIRMED_RESERVATION] as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByRole('button', { name: /delete_reservation res-1/ })).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /delete_reservation res-1/ }));

    expect(screen.getByText('delete_reservation_confirm')).toBeInTheDocument();
  });

  it('should call deleteReservation and show toast on confirm', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([CONFIRMED_RESERVATION] as never);
    vi.mocked(reservationService.deleteReservation).mockResolvedValueOnce(undefined as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByRole('button', { name: /delete_reservation res-1/ })).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /delete_reservation res-1/ }));
    fireEvent.click(screen.getByRole('button', { name: 'confirm' }));

    await waitFor(() => {
      expect(reservationService.deleteReservation).toHaveBeenCalledWith('res-1');
      expect(mockAddToast).toHaveBeenCalledWith('reservation_deleted_success', 'success');
    });
  });

  it('should remove the reservation from the list after successful delete', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([CONFIRMED_RESERVATION] as never);
    vi.mocked(reservationService.deleteReservation).mockResolvedValueOnce(undefined as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /delete_reservation res-1/ }));
    fireEvent.click(screen.getByRole('button', { name: 'confirm' }));

    await waitFor(() => expect(screen.queryByText('John Doe')).not.toBeInTheDocument());
  });

  it('should filter reservations by guest name on search input', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([
      CONFIRMED_RESERVATION,
      { ...CONFIRMED_RESERVATION, id: 'res-3', guestFullName: 'Jane Smith' },
    ] as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());

    const input = screen.getByRole('searchbox');
    fireEvent.change(input, { target: { value: 'Jane' } });

    await waitFor(() => {
      expect(screen.queryByText('John Doe')).not.toBeInTheDocument();
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
    }, { timeout: 500 });
  });

  it('sorts by check-in date descending by default', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([
      { ...CONFIRMED_RESERVATION, id: 'res-early', guestFullName: 'Early Guest', checkInDate: '2026-01-01', checkOutDate: '2026-01-05' },
      { ...CONFIRMED_RESERVATION, id: 'res-late', guestFullName: 'Late Guest', checkInDate: '2026-06-01', checkOutDate: '2026-06-05' },
    ] as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('Late Guest')).toBeInTheDocument());
    const rows = screen.getAllByText(/Guest$/);
    expect(rows[0]).toHaveTextContent('Late Guest');
    expect(rows[1]).toHaveTextContent('Early Guest');
  });

  it('re-sorts by check-out date when the sort field is changed', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([
      { ...CONFIRMED_RESERVATION, id: 'res-early', guestFullName: 'Early Guest', checkInDate: '2026-06-01', checkOutDate: '2026-01-05' },
      { ...CONFIRMED_RESERVATION, id: 'res-late', guestFullName: 'Late Guest', checkInDate: '2026-01-01', checkOutDate: '2026-06-05' },
    ] as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('Early Guest')).toBeInTheDocument());
    fireEvent.change(screen.getByLabelText('sort_by'), { target: { value: 'checkOutDate' } });

    const rows = screen.getAllByText(/Guest$/);
    expect(rows[0]).toHaveTextContent('Late Guest');
    expect(rows[1]).toHaveTextContent('Early Guest');
  });

  it('reverses sort order when the direction toggle is clicked', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([
      { ...CONFIRMED_RESERVATION, id: 'res-early', guestFullName: 'Early Guest', checkInDate: '2026-01-01', checkOutDate: '2026-01-05' },
      { ...CONFIRMED_RESERVATION, id: 'res-late', guestFullName: 'Late Guest', checkInDate: '2026-06-01', checkOutDate: '2026-06-05' },
    ] as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('Late Guest')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'sort_dir_desc' }));

    const rows = screen.getAllByText(/Guest$/);
    expect(rows[0]).toHaveTextContent('Early Guest');
    expect(rows[1]).toHaveTextContent('Late Guest');
  });

  it('should navigate to check-in when check-in button is clicked on CONFIRMED reservation', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([CONFIRMED_RESERVATION] as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);

    await waitFor(() => expect(screen.getByRole('button', { name: 'check_in' })).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'check_in' }));

    expect(mockNavigate).toHaveBeenCalledWith(
      '/stays/check-in/res-1',
      expect.objectContaining({ state: expect.objectContaining({ guestId: 'g1' }) })
    );
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

  it('hides past reservations when the "upcoming only" filter is toggled on', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([
      { ...CONFIRMED_RESERVATION, id: 'res-past', guestFullName: 'Past Guest', checkInDate: '2000-01-01', checkOutDate: '2000-01-05' },
      { ...CONFIRMED_RESERVATION, id: 'res-future', guestFullName: 'Future Guest', checkInDate: '2099-01-01', checkOutDate: '2099-01-05' },
    ] as never);
    render(<MemoryRouter><Reservations /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText('Past Guest')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'reservations_upcoming_filter' }));

    await waitFor(() => {
      expect(screen.queryByText('Past Guest')).not.toBeInTheDocument();
      expect(screen.getByText('Future Guest')).toBeInTheDocument();
    });
  });

  it('applies upcomingOnly, sortField and sortDir from navigation state on initial load', async () => {
    vi.mocked(reservationService.getAllReservations).mockResolvedValueOnce([
      { ...CONFIRMED_RESERVATION, id: 'res-past', guestFullName: 'Past Guest', checkInDate: '2000-01-01', checkOutDate: '2000-01-05' },
      { ...CONFIRMED_RESERVATION, id: 'res-future', guestFullName: 'Future Guest', checkInDate: '2099-01-01', checkOutDate: '2099-01-05' },
    ] as never);
    render(
      <MemoryRouter initialEntries={[{ pathname: '/reservations', state: { upcomingOnly: true, sortField: 'checkInDate', sortDir: 'asc' } }]}>
        <Reservations />
      </MemoryRouter>
    );

    await waitFor(() => expect(screen.getByText('Future Guest')).toBeInTheDocument());
    expect(screen.queryByText('Past Guest')).not.toBeInTheDocument();
  });
});
