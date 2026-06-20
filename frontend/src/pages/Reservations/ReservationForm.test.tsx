import type { ChangeEvent } from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
/* eslint-disable react-perf/jsx-no-new-array-as-prop, react-perf/jsx-no-new-function-as-prop -- test-only mock components, not the real perf-sensitive render path */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { ReservationForm } from './ReservationForm';
import { inventoryService } from '../../services/inventoryService';
import { reservationService } from '../../services/reservationService';
import { guestService } from '../../services/guestService';

import type { RoomResponse } from '../../types/inventory.types';
import type { GuestResponseDTO } from '../../types/guest.types';
import type { ReservationResponse } from '../../types/reservation.types';

// Mock the services
vi.mock('../../services/inventoryService');
vi.mock('../../services/reservationService');
vi.mock('../../services/guestService');

interface GuestMockProps {
  selectedGuest: { firstName: string; lastName: string } | null;
  onSelectGuest: (g: { id: string; firstName: string; lastName: string }) => void;
  onClearGuest: () => void;
  readOnly?: boolean;
}

function GuestSearchAndCreateMock({ selectedGuest, onSelectGuest, onClearGuest, readOnly }: GuestMockProps) {
  const handleSelect = () => onSelectGuest({ id: 'g1', firstName: 'Mario', lastName: 'Rossi' });
  return (
    <div data-testid="guest-mock">
      {selectedGuest ? `${selectedGuest.firstName} ${selectedGuest.lastName}` : 'No Guest'}
      {readOnly && <span>Read Only</span>}
      {!readOnly && (
        <>
          <button type="button" onClick={handleSelect}>Select Guest</button>
          <button type="button" onClick={onClearGuest}>Clear Guest</button>
        </>
      )}
    </div>
  );
}

vi.mock('./GuestSearchAndCreate', () => ({
  GuestSearchAndCreate: (props: GuestMockProps) => GuestSearchAndCreateMock(props),
}));

interface RoomMockProps {
  readOnly?: boolean;
  onCheckInChange: (v: string) => void;
  onCheckOutChange: (v: string) => void;
  onToggleRoom: (roomId: string) => void;
  selectedRoomIds: string[];
}

function RoomSelectionMock({ readOnly, onCheckInChange, onCheckOutChange, onToggleRoom, selectedRoomIds }: RoomMockProps) {
  const handleCheckIn = (e: ChangeEvent<HTMLInputElement>) => onCheckInChange(e.target.value);
  const handleCheckOut = (e: ChangeEvent<HTMLInputElement>) => onCheckOutChange(e.target.value);
  const handleToggle = () => onToggleRoom('r1');
  return (
    <div data-testid="room-mock">
      Room Mock
      {readOnly && <span>Read Only</span>}
      {!readOnly && (
        <>
          <label htmlFor="mock-checkin">Mock Check-in</label>
          <input id="mock-checkin" onChange={handleCheckIn} />
          <label htmlFor="mock-checkout">Mock Check-out</label>
          <input id="mock-checkout" onChange={handleCheckOut} />
          <button type="button" onClick={handleToggle}>Toggle Room r1</button>
          <span>Selected: {selectedRoomIds.join(',')}</span>
        </>
      )}
    </div>
  );
}

vi.mock('./RoomSelection', () => ({
  RoomSelection: (props: RoomMockProps) => RoomSelectionMock(props),
}));

// `t` must be a module-level stable reference: ReservationForm's loadInitialData
// useCallback depends on `t`, and that callback is the sole effect dependency that
// triggers the initial fetch. An inline arrow recreated on every useTranslation()
// call would give `t` (and therefore loadInitialData) a new identity on every
// re-render, silently re-running the fetch — and its setFetching(true)/setError(null)
// resets — after every single interaction in this test file.
const stableT = (key: string) => key;
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: stableT, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const mockNavigate = vi.fn();

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// ---------------------------------------------------------------------------
// Mock data factories
// ---------------------------------------------------------------------------
const mockRoom = (overrides: Partial<RoomResponse> = {}): RoomResponse => ({
  id: 'r1',
  roomNumber: '101',
  type: 'SINGLE',
  pricePerNight: 100,
  status: 'CLEAN',
  active: true,
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
  ...overrides,
} as unknown as RoomResponse);

const mockGuest = (overrides: Partial<GuestResponseDTO> = {}): GuestResponseDTO => ({
  id: 'g1',
  firstName: 'John',
  lastName: 'Doe',
  email: 'john@example.com',
  active: true,
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
  ...overrides,
});

const mockReservation = (overrides: Partial<ReservationResponse> = {}): ReservationResponse => ({
  id: 'res1',
  guestId: 'g2',
  guestFullName: 'John Doe',
  checkInDate: '2026-03-20',
  checkOutDate: '2026-03-24',
  status: 'CONFIRMED',
  expectedGuests: 3,
  lineItems: [],
  active: true,
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
  ...overrides,
});

describe('ReservationForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue({
      content: [
        mockRoom({ id: 'r1', roomNumber: '101' }),
        mockRoom({ id: 'r2', roomNumber: '102' }),
      ],
      totalElements: 2,
    } as never);
    vi.mocked(reservationService.getAllReservations).mockResolvedValue([]);
  });

  it('renders correctly and loads rooms in "New" mode', async () => {
    render(
      <MemoryRouter initialEntries={['/reservations/new']}>
        <Routes>
          <Route path="/reservations/new" element={<ReservationForm />} />
        </Routes>
      </MemoryRouter>
    );
    
    await waitFor(() => {
      expect(screen.getByText('new_reservation')).toBeInTheDocument();
      expect(screen.getByTestId('room-mock')).toBeInTheDocument();
    });
  });

  it('renders correctly in "View" mode', async () => {
    vi.mocked(reservationService.getReservationById).mockResolvedValue(mockReservation({
      id: 'res123',
      guestId: 'g1',
    }));
    vi.mocked(guestService.getGuestById).mockResolvedValue(mockGuest({ id: 'g1', firstName: 'Mario', lastName: 'Rossi' }));

    render(
      <MemoryRouter initialEntries={['/reservations/res123']}>
        <Routes>
          <Route path="/reservations/:id" element={<ReservationForm />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('reservation_details')).toBeInTheDocument();
      expect(screen.getByText(/Mario Rossi/i)).toBeInTheDocument();
      expect(screen.getAllByText(/Read Only/i).length).toBeGreaterThan(0);
    });

    expect(screen.queryByRole('button', { name: /confirm_reservation|update_reservation/i })).not.toBeInTheDocument();
  });

  it('renders correctly in "Edit" mode', async () => {
    vi.mocked(reservationService.getReservationById).mockResolvedValue(mockReservation({
      id: 'res123',
      guestId: 'g1',
    }));
    vi.mocked(guestService.getGuestById).mockResolvedValue(mockGuest({ id: 'g1', firstName: 'Luigi', lastName: 'Verdi' }));

    render(
      <MemoryRouter initialEntries={['/reservations/edit/res123']}>
        <Routes>
          <Route path="/reservations/edit/:id" element={<ReservationForm />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('edit_reservation')).toBeInTheDocument();
      expect(screen.getByText(/Luigi Verdi/i)).toBeInTheDocument();
    });

    expect(screen.getByRole('button', { name: /update_reservation/i })).toBeInTheDocument();
  });

  it('should have no accessibility violations', async () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/reservations/new']}>
        <Routes>
          <Route path="/reservations/new" element={<ReservationForm />} />
        </Routes>
      </MemoryRouter>
    );
    await waitFor(() => expect(screen.getByText('new_reservation')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });

  const renderNew = () => render(
    <MemoryRouter initialEntries={['/reservations/new']}>
      <Routes>
        <Route path="/reservations/new" element={<ReservationForm />} />
      </Routes>
    </MemoryRouter>
  );

  // The submit button is disabled until guest+room+both dates are set, so the
  // validation-chain tests (which submit partial state on purpose) must fire
  // the form's submit event directly rather than clicking the (disabled) button.
  const submitForm = () => fireEvent.submit(document.querySelector('form')!);

  describe('validation chain', () => {
    it('shows msg_select_guest when no guest is selected', async () => {
      renderNew();
      await waitFor(() => screen.getByText('new_reservation'));
      submitForm();
      expect(await screen.findByText('msg_select_guest')).toBeInTheDocument();
    });

    it('shows msg_select_room when a guest is selected but no room', async () => {
      renderNew();
      await waitFor(() => screen.getByText('new_reservation'));
      fireEvent.click(screen.getByText('Select Guest'));
      submitForm();
      expect(await screen.findByText('msg_select_room')).toBeInTheDocument();
    });

    it('shows msg_valid_dates when guest and room are set but dates are missing', async () => {
      renderNew();
      await waitFor(() => screen.getByText('new_reservation'));
      fireEvent.click(screen.getByText('Select Guest'));
      fireEvent.click(screen.getByText('Toggle Room r1'));
      submitForm();
      expect(await screen.findByText('msg_valid_dates')).toBeInTheDocument();
    });

    it('clears the selected guest via onClearGuest', async () => {
      renderNew();
      await waitFor(() => screen.getByText('new_reservation'));
      fireEvent.click(screen.getByText('Select Guest'));
      expect(await screen.findByText('Mario Rossi')).toBeInTheDocument();
      fireEvent.click(screen.getByText('Clear Guest'));
      expect(await screen.findByText('No Guest')).toBeInTheDocument();
    });

    it('shows reservation_overlap_error when the chosen room overlaps an existing booking', async () => {
      vi.mocked(reservationService.getAllReservations).mockResolvedValue([
        mockReservation({
          id: 'other-res', guestId: 'g2', status: 'CONFIRMED',
          checkInDate: '2026-03-20', checkOutDate: '2026-03-24',
          lineItems: [{ roomId: 'r1', active: true } as never],
        }),
      ]);
      renderNew();
      await waitFor(() => screen.getByText('new_reservation'));
      fireEvent.click(screen.getByText('Select Guest'));
      fireEvent.click(screen.getByText('Toggle Room r1'));
      fireEvent.change(screen.getByLabelText('Mock Check-in'), { target: { value: '2026-03-22' } });
      fireEvent.change(screen.getByLabelText('Mock Check-out'), { target: { value: '2026-03-26' } });
      submitForm();
      expect(await screen.findByText('reservation_overlap_error')).toBeInTheDocument();
      expect(reservationService.createReservation).not.toHaveBeenCalled();
    });

    it('ignores overlap against a CANCELLED reservation on the same room', async () => {
      vi.mocked(reservationService.getAllReservations).mockResolvedValue([
        mockReservation({
          id: 'other-res', guestId: 'g2', status: 'CANCELLED',
          checkInDate: '2026-03-20', checkOutDate: '2026-03-24',
          lineItems: [{ roomId: 'r1', active: true } as never],
        }),
      ]);
      vi.mocked(reservationService.createReservation).mockResolvedValue(mockReservation());
      renderNew();
      await waitFor(() => screen.getByText('new_reservation'));
      fireEvent.click(screen.getByText('Select Guest'));
      fireEvent.click(screen.getByText('Toggle Room r1'));
      fireEvent.change(screen.getByLabelText('Mock Check-in'), { target: { value: '2026-03-22' } });
      fireEvent.change(screen.getByLabelText('Mock Check-out'), { target: { value: '2026-03-26' } });
      submitForm();
      await waitFor(() => expect(reservationService.createReservation).toHaveBeenCalledTimes(1));
    });
  });

  describe('submission', () => {
    it('creates a reservation and navigates back on success', async () => {
      vi.mocked(reservationService.createReservation).mockResolvedValue(mockReservation());
      renderNew();
      await waitFor(() => screen.getByText('new_reservation'));
      fireEvent.click(screen.getByText('Select Guest'));
      fireEvent.click(screen.getByText('Toggle Room r1'));
      fireEvent.change(screen.getByLabelText('Mock Check-in'), { target: { value: '2026-04-01' } });
      fireEvent.change(screen.getByLabelText('Mock Check-out'), { target: { value: '2026-04-03' } });
      submitForm();

      await waitFor(() => expect(reservationService.createReservation).toHaveBeenCalledWith(
        expect.objectContaining({ guestId: 'g1', checkInDate: '2026-04-01', checkOutDate: '2026-04-03' }),
      ));
      expect(mockNavigate).toHaveBeenCalledWith('/reservations');
    });

    it('updates an existing reservation in edit mode', async () => {
      vi.mocked(reservationService.getReservationById).mockResolvedValue(mockReservation({ id: 'res123', guestId: 'g1' }));
      vi.mocked(guestService.getGuestById).mockResolvedValue(mockGuest({ id: 'g1' }));
      vi.mocked(reservationService.updateReservation).mockResolvedValue(mockReservation());

      render(
        <MemoryRouter initialEntries={['/reservations/edit/res123']}>
          <Routes><Route path="/reservations/edit/:id" element={<ReservationForm />} /></Routes>
        </MemoryRouter>
      );
      await waitFor(() => screen.getByText('edit_reservation'));
      fireEvent.click(screen.getByText('Toggle Room r1'));
      fireEvent.change(screen.getByLabelText('Mock Check-in'), { target: { value: '2026-05-01' } });
      fireEvent.change(screen.getByLabelText('Mock Check-out'), { target: { value: '2026-05-03' } });
      fireEvent.click(screen.getByRole('button', { name: /update_reservation/i }));

      await waitFor(() => expect(reservationService.updateReservation).toHaveBeenCalledWith('res123', expect.anything()));
      expect(mockNavigate).toHaveBeenCalledWith('/reservations');
    });

    it('shows err_guest_not_found when the backend rejects with that error code', async () => {
      vi.mocked(reservationService.createReservation).mockRejectedValue({
        response: { data: { errorCode: 'GUEST_NOT_FOUND' } },
      });
      renderNew();
      await waitFor(() => screen.getByText('new_reservation'));
      fireEvent.click(screen.getByText('Select Guest'));
      fireEvent.click(screen.getByText('Toggle Room r1'));
      fireEvent.change(screen.getByLabelText('Mock Check-in'), { target: { value: '2026-04-01' } });
      fireEvent.change(screen.getByLabelText('Mock Check-out'), { target: { value: '2026-04-03' } });
      submitForm();

      expect(await screen.findByText('err_guest_not_found')).toBeInTheDocument();
    });

    it('shows a generic failure message on a non-specific creation error', async () => {
      vi.mocked(reservationService.createReservation).mockRejectedValue({ response: { data: {} } });
      renderNew();
      await waitFor(() => screen.getByText('new_reservation'));
      fireEvent.click(screen.getByText('Select Guest'));
      fireEvent.click(screen.getByText('Toggle Room r1'));
      fireEvent.change(screen.getByLabelText('Mock Check-in'), { target: { value: '2026-04-01' } });
      fireEvent.change(screen.getByLabelText('Mock Check-out'), { target: { value: '2026-04-03' } });
      submitForm();

      expect(await screen.findByText('failed_create_reservation')).toBeInTheDocument();
    });
  });

  describe('loadInitialData error handling', () => {
    it('shows an error when getAllRooms fails', async () => {
      vi.mocked(inventoryService.getAllRooms).mockRejectedValue({ response: { data: { detail: 'rooms down' } } });
      renderNew();
      expect(await screen.findByText('rooms down')).toBeInTheDocument();
    });

    it('falls back to guestFullName when getGuestById fails in edit mode', async () => {
      vi.mocked(reservationService.getReservationById).mockResolvedValue(mockReservation({
        id: 'res123', guestId: 'g404', guestFullName: 'Fallback Guest',
      }));
      vi.mocked(guestService.getGuestById).mockRejectedValue(new Error('not found'));

      render(
        <MemoryRouter initialEntries={['/reservations/edit/res123']}>
          <Routes><Route path="/reservations/edit/:id" element={<ReservationForm />} /></Routes>
        </MemoryRouter>
      );
      expect(await screen.findByText(/Fallback Guest/i)).toBeInTheDocument();
    });
  });

  it('navigates back to the list when the back button is clicked', async () => {
    renderNew();
    await waitFor(() => screen.getByText('new_reservation'));
    fireEvent.click(screen.getByLabelText('back'));
    expect(mockNavigate).toHaveBeenCalledWith('/reservations');
  });
});
