import type { ReactElement } from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { renderWithQuery, mockAxiosErrorWithDetail, openRowMenu } from '../../test-utils';
import { NoShowCandidatesList } from './NoShowCandidatesList';
import { reservationService, inventoryService } from '../../services';
import { useToastStore } from '../../store';

const mockNavigate = vi.hoisted(() => vi.fn());
vi.mock('react-router-dom', async (orig) => ({
  ...(await orig<typeof import('react-router-dom')>()),
  useNavigate: () => mockNavigate,
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => (opts ? `${key} ${JSON.stringify(opts)}` : key),
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/reservationService', () => ({
  reservationService: { searchReservations: vi.fn(), updateStatus: vi.fn() },
}));
vi.mock('../../services/inventoryService', () => ({
  inventoryService: { getAllRooms: vi.fn() },
}));
vi.mock('../../store/toastStore', () => ({ useToastStore: vi.fn() }));
vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const render = (ui: ReactElement) => renderWithQuery(<MemoryRouter>{ui}</MemoryRouter>);

const reservation = (overrides: Record<string, unknown> = {}) => ({
  id: 'r1', guestId: 'g1', guestFullName: 'Jane Doe', checkInDate: '2026-06-14', checkOutDate: '2099-01-01',
  status: 'CONFIRMED', expectedGuests: 2, lineItems: [{ id: 'li1', roomId: 'room1' }], active: true, version: 3,
  ...overrides,
});
const page = (content: unknown[], totalElements = content.length) => ({ content, totalPages: 1, totalElements });
const mockAddToast = vi.fn();

/** Serves PENDING and CONFIRMED searches separately, as the list issues one query per status. */
const mockSearch = (pending: unknown[], confirmed: unknown[], confirmedTotal = confirmed.length) => {
  vi.mocked(reservationService.searchReservations).mockImplementation(((params: { status?: string }) =>
    Promise.resolve(params.status === 'PENDING' ? page(pending) : page(confirmed, confirmedTotal))) as never);
};

const clickAndConfirm = async (actionName: string) => {
  await screen.findByText('Jane Doe');
  openRowMenu();
  fireEvent.click(screen.getByRole('menuitem', { name: actionName }));
  fireEvent.click(screen.getByRole('button', { name: 'confirm' }));
};

describe('NoShowCandidatesList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useToastStore).mockReturnValue(mockAddToast);
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue({ content: [{ id: 'room1', roomNumber: '201' }] } as never);
    mockSearch([], []);
  });

  it('queries PENDING and CONFIRMED up to the business date', async () => {
    render(<NoShowCandidatesList businessDate="2026-06-14" />);

    await waitFor(() => expect(reservationService.searchReservations).toHaveBeenCalledTimes(2));
    const calls = vi.mocked(reservationService.searchReservations).mock.calls.map(([p]) => p);
    expect(calls.map((p) => p.status).sort()).toEqual(['CONFIRMED', 'PENDING']);
    expect(calls.every((p) => p.dateTo === '2026-06-14')).toBe(true);
  });

  it('merges both statuses ordered by check-in, with room number or a dash', async () => {
    mockSearch(
      [reservation({ id: 'p1', guestFullName: 'Pia Pending', status: 'PENDING', checkInDate: '2026-06-14', lineItems: [] })],
      [reservation({ id: 'c1', guestFullName: 'Carl Confirmed', checkInDate: '2026-06-12' })],
    );
    render(<NoShowCandidatesList businessDate="2026-06-14" />);

    await waitFor(() => expect(screen.getByText('Carl Confirmed')).toBeInTheDocument());
    const names = screen.getAllByText(/Carl Confirmed|Pia Pending/).map((n) => n.textContent);
    expect(names).toEqual(['Carl Confirmed', 'Pia Pending']);
    await waitFor(() => expect(screen.getByText('201')).toBeInTheDocument());
    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('shows the empty state when there is nothing to resolve', async () => {
    render(<NoShowCandidatesList businessDate="2026-06-14" />);
    await waitFor(() => expect(screen.getByText('night_audit_noshows_empty')).toBeInTheDocument());
  });

  it('shows a non-blocking error when the search fails', async () => {
    vi.mocked(reservationService.searchReservations).mockRejectedValue(new Error('boom'));
    render(<NoShowCandidatesList businessDate="2026-06-14" />);
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
  });

  it('tells how many candidates are not listed', async () => {
    mockSearch([], [reservation()], 60);
    render(<NoShowCandidatesList businessDate="2026-06-14" />);
    await waitFor(() => expect(screen.getByText(/night_audit_noshows_more.*"count":59/)).toBeInTheDocument());
  });

  it('marks a no-show after confirmation, sending the reservation version', async () => {
    mockSearch([], [reservation()]);
    vi.mocked(reservationService.updateStatus).mockResolvedValue(reservation({ status: 'NO_SHOW' }) as never);
    render(<NoShowCandidatesList businessDate="2026-06-14" />);

    await clickAndConfirm('mark_no_show');

    await waitFor(() => expect(reservationService.updateStatus).toHaveBeenCalledWith('r1', 'NO_SHOW', 3));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('no_show_marked_success', 'success'));
  });

  it('cancels a reservation after confirmation, treating version 0 as valid', async () => {
    mockSearch([reservation({ status: 'PENDING', version: 0 })], []);
    vi.mocked(reservationService.updateStatus).mockResolvedValue(reservation({ status: 'CANCELLED' }) as never);
    render(<NoShowCandidatesList businessDate="2026-06-14" />);

    await screen.findByText('Jane Doe');

    openRowMenu();

    fireEvent.click(screen.getByRole('menuitem', { name: 'cancel_reservation' }));
    expect(screen.getByText('cancel_reservation_confirm')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'confirm' }));

    await waitFor(() => expect(reservationService.updateStatus).toHaveBeenCalledWith('r1', 'CANCELLED', 0));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('reservation_cancelled_success', 'success'));
  });

  it('shows the backend detail when the status change is rejected', async () => {
    mockSearch([], [reservation()]);
    vi.mocked(reservationService.updateStatus).mockRejectedValue(mockAxiosErrorWithDetail('RESERVATION_NO_SHOW_HAS_STAY', 409));
    render(<NoShowCandidatesList businessDate="2026-06-14" />);

    await clickAndConfirm('mark_no_show');

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('RESERVATION_NO_SHOW_HAS_STAY', 'error'));
  });

  it('reloads the list after a rejected change so a retry uses the fresh version', async () => {
    mockSearch([], [reservation()]);
    vi.mocked(reservationService.updateStatus).mockRejectedValue(mockAxiosErrorWithDetail('RESERVATION_STALE_VERSION', 409));
    render(<NoShowCandidatesList businessDate="2026-06-14" />);
    await screen.findByText('Jane Doe');
    const before = vi.mocked(reservationService.searchReservations).mock.calls.length;

    await clickAndConfirm('mark_no_show');

    await waitFor(() => expect(vi.mocked(reservationService.searchReservations).mock.calls.length).toBeGreaterThan(before));
  });

  it('closes the dialog on cancel without sending anything', async () => {
    mockSearch([], [reservation()]);
    render(<NoShowCandidatesList businessDate="2026-06-14" />);

    await screen.findByText('Jane Doe');

    openRowMenu();

    fireEvent.click(screen.getByRole('menuitem', { name: 'mark_no_show' }));
    fireEvent.click(screen.getByRole('button', { name: 'cancel' }));

    expect(screen.queryByText('mark_no_show_confirm')).not.toBeInTheDocument();
    expect(reservationService.updateStatus).not.toHaveBeenCalled();
  });

  it('refuses to send the change when the version is unknown', async () => {
    mockSearch([], [reservation({ version: undefined })]);
    render(<NoShowCandidatesList businessDate="2026-06-14" />);

    await clickAndConfirm('mark_no_show');

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('mark_no_show_failed', 'error'));
    expect(reservationService.updateStatus).not.toHaveBeenCalled();
  });

  it('offers check-in only for CONFIRMED and navigates with the room and guests', async () => {
    mockSearch([reservation({ id: 'p1', guestFullName: 'Pia Pending', status: 'PENDING' })], [reservation()]);
    render(<NoShowCandidatesList businessDate="2026-06-14" />);

    await screen.findByText('Jane Doe');
    expect(screen.queryByRole('button', { name: 'check_in Pia Pending' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'check_in Jane Doe' }));

    expect(mockNavigate).toHaveBeenCalledWith('/stays/check-in/r1', {
      state: { roomId: 'room1', expectedGuests: 2, guestId: 'g1' },
    });
  });

  it('opens the reservation editor', async () => {
    mockSearch([], [reservation()]);
    render(<NoShowCandidatesList businessDate="2026-06-14" />);

    await screen.findByText('Jane Doe');

    openRowMenu();

    fireEvent.click(screen.getByRole('menuitem', { name: 'edit' }));
    expect(mockNavigate).toHaveBeenCalledWith('/reservations/edit/r1');
  });

  it('has no accessibility violations', async () => {
    mockSearch([], [reservation()]);
    const { container } = render(<NoShowCandidatesList businessDate="2026-06-14" />);
    await screen.findByText('Jane Doe');
    expect(await axe(container)).toHaveNoViolations();
  });
});
