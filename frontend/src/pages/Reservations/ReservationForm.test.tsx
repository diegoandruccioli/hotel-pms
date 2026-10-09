import type { ChangeEvent } from 'react';
import { render, screen, waitFor, fireEvent, within } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
/* eslint-disable react-perf/jsx-no-new-array-as-prop, react-perf/jsx-no-new-function-as-prop -- test-only mock components, not the real perf-sensitive render path */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { ReservationForm } from './ReservationForm';
import { inventoryService } from '../../services';
import { reservationService } from '../../services';
import { guestService } from '../../services';
import { stayService } from '../../services';

import type { RoomResponse } from '../../types';
import type { GuestResponseDTO } from '../../types';
import type { ReservationResponse } from '../../types';

// Mock the services
vi.mock('../../services/inventoryService');
vi.mock('../../services/reservationService');
vi.mock('../../services/guestService');
vi.mock('../../services/stayService');

// M3Dialog (stale-version conflict dialog) uses focus-trap-react, which requires a
// real tabbable node inside the DOM to activate — jsdom's layout stubs make that
// unreliable in tests. Same mock as every other M3Dialog test in this codebase.
vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

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

interface DatesMockProps {
  readOnly?: boolean;
  onCheckInChange: (v: string) => void;
  onCheckOutChange: (v: string) => void;
}

function StayDatesFieldsMock({ readOnly, onCheckInChange, onCheckOutChange }: DatesMockProps) {
  const handleCheckIn = (e: ChangeEvent<HTMLInputElement>) => onCheckInChange(e.target.value);
  const handleCheckOut = (e: ChangeEvent<HTMLInputElement>) => onCheckOutChange(e.target.value);
  return (
    <div data-testid="dates-mock">
      {readOnly && <span>Read Only</span>}
      {!readOnly && (
        <>
          <label htmlFor="mock-checkin">Mock Check-in</label>
          <input id="mock-checkin" onChange={handleCheckIn} />
          <label htmlFor="mock-checkout">Mock Check-out</label>
          <input id="mock-checkout" onChange={handleCheckOut} />
        </>
      )}
    </div>
  );
}

vi.mock('./StayDatesFields', () => ({
  StayDatesFields: (props: DatesMockProps) => StayDatesFieldsMock(props),
}));

interface RoomGridMockProps {
  readOnly?: boolean;
  onToggleRoom: (roomId: string) => void;
  selectedRoomIds: string[];
}

function RoomGridMock({ readOnly, onToggleRoom, selectedRoomIds }: RoomGridMockProps) {
  const handleToggle = () => onToggleRoom('r1');
  return (
    <div data-testid="room-mock">
      {readOnly && <span>Read Only</span>}
      {!readOnly && (
        <>
          <button type="button" onClick={handleToggle}>Toggle Room r1</button>
          <span>Selected: {selectedRoomIds.join(',')}</span>
        </>
      )}
    </div>
  );
}

vi.mock('./RoomGrid', () => ({
  RoomGrid: (props: RoomGridMockProps) => RoomGridMock(props),
}));

// `t` must be a module-level stable reference: the hook's loadInitialData
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
  confirmationEmailFailed: false,
  version: 3,
  ...overrides,
});

// ---------------------------------------------------------------------------
// Render + flow helpers
// ---------------------------------------------------------------------------
const renderNew = () => render(
  <MemoryRouter initialEntries={['/reservations/new']}>
    <Routes>
      <Route path="/reservations/new" element={<ReservationForm />} />
    </Routes>
  </MemoryRouter>
);

const renderNewWithState = (state: unknown) => render(
  <MemoryRouter initialEntries={[{ pathname: '/reservations/new', state }]}>
    <Routes>
      <Route path="/reservations/new" element={<ReservationForm />} />
    </Routes>
  </MemoryRouter>
);

const renderEdit = () => render(
  <MemoryRouter initialEntries={['/reservations/edit/res123']}>
    <Routes><Route path="/reservations/edit/:id" element={<ReservationForm />} /></Routes>
  </MemoryRouter>
);

const renderView = () => render(
  <MemoryRouter initialEntries={['/reservations/res123']}>
    <Routes><Route path="/reservations/:id" element={<ReservationForm />} /></Routes>
  </MemoryRouter>
);

const waitForTitle = (name: string) => waitFor(() => screen.getByRole('heading', { level: 1, name }));
const stepHeading = (name: string) => screen.getByRole('heading', { level: 2, name });
const clickNext = () => fireEvent.click(screen.getByRole('button', { name: 'btn_next' }));
const fillDates = (checkIn: string, checkOut: string) => {
  fireEvent.change(screen.getByLabelText('Mock Check-in'), { target: { value: checkIn } });
  fireEvent.change(screen.getByLabelText('Mock Check-out'), { target: { value: checkOut } });
};
const toggleRoom = () => fireEvent.click(screen.getByText('Toggle Room r1'));
const selectGuest = () => fireEvent.click(screen.getByText('Select Guest'));

/** Walks a new reservation through every step and stops on the summary. */
const completeNewFlow = (checkIn = '2026-04-01', checkOut = '2026-04-03') => {
  fillDates(checkIn, checkOut);
  clickNext();
  toggleRoom();
  clickNext();
  selectGuest();
  clickNext();
};

/** In edit mode every step is reachable: fill dates and a room straight from the stepper. */
const fillEditReservation = () => {
  fillDates('2026-05-01', '2026-05-03');
  fireEvent.click(screen.getByRole('button', { name: 'reservation_step_rooms' }));
  toggleRoom();
};

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
    // Resolved-price lookup: an empty list keeps the effect's promise from resolving
    // to undefined and throwing on `.then`; individual tests override it.
    vi.mocked(inventoryService.getAvailableRooms).mockResolvedValue([]);
    vi.mocked(stayService.getStaysByReservationId).mockResolvedValue(
      { content: [], totalElements: 0 } as never,
    );
  });

  describe('modes', () => {
    it('renders the first step with the stepper and the summary panel in "New" mode', async () => {
      renderNew();
      await waitForTitle('new_reservation');

      expect(screen.getByRole('list', { name: 'stepper_aria_label' })).toBeInTheDocument();
      expect(stepHeading('reservation_step_dates')).toBeInTheDocument();
      expect(screen.getByTestId('dates-mock')).toBeInTheDocument();
      expect(screen.getByRole('region', { name: 'summary_title' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'btn_next' })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'btn_previous' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /confirm_reservation/ })).not.toBeInTheDocument();
    });

    it('renders only the read-only summary with Back and Edit in "View" mode', async () => {
      vi.mocked(reservationService.getReservationById).mockResolvedValue(mockReservation({
        id: 'res123',
        guestId: 'g1',
        lineItems: [{ roomId: 'r1', active: true } as never],
      }));
      vi.mocked(guestService.getGuestById).mockResolvedValue(mockGuest({ id: 'g1', firstName: 'Mario', lastName: 'Rossi' }));

      renderView();
      await waitForTitle('reservation_details');

      expect(screen.getByRole('region', { name: 'summary_title' })).toBeInTheDocument();
      expect(screen.getByText(/Mario Rossi/)).toBeInTheDocument();
      expect(screen.getByText('room_number')).toBeInTheDocument();
      expect(screen.queryByRole('list', { name: 'stepper_aria_label' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /confirm_reservation|update_reservation|btn_next/ })).not.toBeInTheDocument();
      // header back arrow + footer Back button
      expect(screen.getAllByRole('button', { name: 'back' })).toHaveLength(2);

      fireEvent.click(screen.getByRole('button', { name: 'edit' }));
      expect(mockNavigate).toHaveBeenCalledWith('/reservations/edit/res123');
    });

    it('lets "Edit" mode jump to any step and save from the summary panel', async () => {
      vi.mocked(reservationService.getReservationById).mockResolvedValue(mockReservation({
        id: 'res123',
        guestId: 'g1',
      }));
      vi.mocked(guestService.getGuestById).mockResolvedValue(mockGuest({ id: 'g1', firstName: 'Luigi', lastName: 'Verdi' }));

      renderEdit();
      await waitForTitle('edit_reservation');

      expect(within(screen.getByRole('region', { name: 'summary_title' })).getByText(/Luigi Verdi/)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /update_reservation/i })).toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: 'reservation_step_summary' }));
      expect(screen.getByRole('button', { name: /update_reservation/i })).toHaveAttribute('type', 'submit');
    });

    it('shows the already-checked-in banner and locks dates and rooms when a CHECKED_IN stay exists', async () => {
      vi.mocked(reservationService.getReservationById).mockResolvedValue(mockReservation({
        id: 'res123',
        guestId: 'g1',
      }));
      vi.mocked(guestService.getGuestById).mockResolvedValue(mockGuest({ id: 'g1', firstName: 'Luigi', lastName: 'Verdi' }));
      vi.mocked(stayService.getStaysByReservationId).mockResolvedValue({
        content: [{ id: 'stay1', status: 'CHECKED_IN' }],
        totalElements: 1,
      } as never);

      renderEdit();

      await waitFor(() => {
        expect(screen.getByText('reservation_already_checked_in_banner')).toBeInTheDocument();
        expect(screen.getByText('Read Only')).toBeInTheDocument();
      });
      expect(screen.getByRole('button', { name: 'reservation_go_to_stay' })).toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: 'reservation_step_rooms' }));
      expect(screen.getByText('Read Only')).toBeInTheDocument();
    });

    it('does not show the already-checked-in banner when no stay is CHECKED_IN', async () => {
      vi.mocked(reservationService.getReservationById).mockResolvedValue(mockReservation({
        id: 'res123',
        guestId: 'g1',
      }));
      vi.mocked(guestService.getGuestById).mockResolvedValue(mockGuest({ id: 'g1', firstName: 'Luigi', lastName: 'Verdi' }));
      vi.mocked(stayService.getStaysByReservationId).mockResolvedValue({
        content: [{ id: 'stay1', status: 'CHECKED_OUT' }],
        totalElements: 1,
      } as never);

      renderEdit();

      await waitForTitle('edit_reservation');
      expect(screen.queryByText('reservation_already_checked_in_banner')).not.toBeInTheDocument();
      expect(screen.queryByText('Read Only')).not.toBeInTheDocument();
    });

    it('has no accessibility violations on the first and the summary step', async () => {
      const { container } = renderNew();
      await waitForTitle('new_reservation');
      expect(await axe(container)).toHaveNoViolations();

      completeNewFlow();
      expect(stepHeading('summary_title')).toBeInTheDocument();
      expect(await axe(container)).toHaveNoViolations();
    });
  });

  describe('preselected guest', () => {
    it('starts with the guest handed over by the guest sheet', async () => {
      renderNewWithState({ guest: mockGuest({ id: 'g9', firstName: 'Anna', lastName: 'Bianchi' }) });
      await waitForTitle('new_reservation');
      expect(within(screen.getByRole('region', { name: 'summary_title' })).getByText('Anna Bianchi')).toBeInTheDocument();
    });

    it('ignores a malformed guest in the router state', async () => {
      renderNewWithState({ guest: { firstName: 42 } });
      await waitForTitle('new_reservation');
      expect(screen.getByText('summary_no_guest')).toBeInTheDocument();
    });

    it('ignores router state that carries no guest', async () => {
      renderNewWithState({ something: 'else' });
      await waitForTitle('new_reservation');
      expect(screen.getByText('summary_no_guest')).toBeInTheDocument();
    });
  });

  describe('step navigation', () => {
    it('does not advance from the dates step without valid dates', async () => {
      renderNew();
      await waitForTitle('new_reservation');
      clickNext();
      expect(await screen.findByText('msg_valid_dates')).toBeInTheDocument();
      expect(stepHeading('reservation_step_dates')).toBeInTheDocument();
    });

    it('does not advance when the checkout date is not after the check-in date', async () => {
      renderNew();
      await waitForTitle('new_reservation');
      fillDates('2026-04-05', '2026-04-01');
      clickNext();
      expect(await screen.findByText('msg_valid_dates')).toBeInTheDocument();
      expect(stepHeading('reservation_step_dates')).toBeInTheDocument();
    });

    it('does not advance from the rooms step without a room', async () => {
      renderNew();
      await waitForTitle('new_reservation');
      fillDates('2026-04-01', '2026-04-03');
      clickNext();
      clickNext();
      expect(await screen.findByText('msg_select_room')).toBeInTheDocument();
      expect(stepHeading('reservation_step_rooms')).toBeInTheDocument();
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
      await waitForTitle('new_reservation');
      fillDates('2026-03-22', '2026-03-26');
      clickNext();
      toggleRoom();
      clickNext();
      expect(await screen.findByText('reservation_overlap_error')).toBeInTheDocument();
      expect(stepHeading('reservation_step_rooms')).toBeInTheDocument();
    });

    it('ignores overlap against a CANCELLED reservation on the same room', async () => {
      vi.mocked(reservationService.getAllReservations).mockResolvedValue([
        mockReservation({
          id: 'other-res', guestId: 'g2', status: 'CANCELLED',
          checkInDate: '2026-03-20', checkOutDate: '2026-03-24',
          lineItems: [{ roomId: 'r1', active: true } as never],
        }),
      ]);
      renderNew();
      await waitForTitle('new_reservation');
      fillDates('2026-03-22', '2026-03-26');
      clickNext();
      toggleRoom();
      clickNext();
      expect(stepHeading('reservation_step_guest')).toBeInTheDocument();
    });

    it('does not advance from the guest step without a guest', async () => {
      renderNew();
      await waitForTitle('new_reservation');
      fillDates('2026-04-01', '2026-04-03');
      clickNext();
      toggleRoom();
      clickNext();
      clickNext();
      expect(await screen.findByText('msg_select_guest')).toBeInTheDocument();
      expect(stepHeading('reservation_step_guest')).toBeInTheDocument();
    });

    it('clears the selected guest via onClearGuest', async () => {
      renderNew();
      await waitForTitle('new_reservation');
      fillDates('2026-04-01', '2026-04-03');
      clickNext();
      toggleRoom();
      clickNext();
      selectGuest();
      expect(within(screen.getByTestId('guest-mock')).getByText('Mario Rossi')).toBeInTheDocument();
      fireEvent.click(screen.getByText('Clear Guest'));
      expect(await screen.findByText('No Guest')).toBeInTheDocument();
    });

    it('goes back with Previous and keeps the values already entered', async () => {
      renderNew();
      await waitForTitle('new_reservation');
      fillDates('2026-04-01', '2026-04-03');
      clickNext();
      toggleRoom();
      expect(stepHeading('reservation_step_rooms')).toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: 'btn_previous' }));
      expect(stepHeading('reservation_step_dates')).toBeInTheDocument();
      clickNext();
      expect(screen.getByText('Selected: r1')).toBeInTheDocument();
    });

    it('only lets a new reservation jump to steps it has already reached', async () => {
      renderNew();
      await waitForTitle('new_reservation');
      expect(screen.queryByRole('button', { name: 'reservation_step_summary' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'reservation_step_rooms' })).not.toBeInTheDocument();

      completeNewFlow();
      fireEvent.click(screen.getByRole('button', { name: 'reservation_step_dates' }));
      expect(stepHeading('reservation_step_dates')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'reservation_step_summary' })).toBeInTheDocument();
    });

    it('does not let a new reservation skip validation by jumping forward in the stepper', async () => {
      renderNew();
      await waitForTitle('new_reservation');
      completeNewFlow();
      fireEvent.click(screen.getByRole('button', { name: 'reservation_step_dates' }));
      fillDates('2026-04-05', '2026-04-01');
      fireEvent.click(screen.getByRole('button', { name: 'reservation_step_summary' }));

      expect(await screen.findByText('msg_valid_dates')).toBeInTheDocument();
      expect(stepHeading('reservation_step_dates')).toBeInTheDocument();
    });

    it('treats Enter on a non-final step as Next, not as a save', async () => {
      renderNew();
      await waitForTitle('new_reservation');
      fillDates('2026-04-01', '2026-04-03');
      fireEvent.submit(document.querySelector('form')!);

      expect(stepHeading('reservation_step_rooms')).toBeInTheDocument();
      expect(reservationService.createReservation).not.toHaveBeenCalled();
    });

    it('does not steal focus on the first render', async () => {
      renderNew();
      await waitForTitle('new_reservation');
      expect(stepHeading('reservation_step_dates')).not.toHaveFocus();
    });

    it('moves focus to the new step title', async () => {
      renderNew();
      await waitForTitle('new_reservation');
      fillDates('2026-04-01', '2026-04-03');
      clickNext();
      expect(stepHeading('reservation_step_rooms')).toHaveFocus();
    });
  });

  describe('summary', () => {
    it('shows the estimated total when every selected room has a resolved price', async () => {
      vi.mocked(inventoryService.getAvailableRooms).mockResolvedValue([
        mockRoom({ id: 'r1', resolvedTotalPrice: 240 }),
      ]);
      renderNew();
      await waitForTitle('new_reservation');
      fillDates('2026-04-01', '2026-04-03');
      clickNext();
      toggleRoom();

      expect(await screen.findByText('summary_estimated_total')).toBeInTheDocument();
      expect(screen.getByText('summary_price_note')).toBeInTheDocument();
      expect(screen.getAllByText(/240/).length).toBeGreaterThan(0);
    });

    it('says the price is calculated on confirmation when a room has no resolved price', async () => {
      renderNew();
      await waitForTitle('new_reservation');
      fillDates('2026-04-01', '2026-04-03');
      clickNext();
      toggleRoom();

      expect(await screen.findByText('summary_price_on_confirm')).toBeInTheDocument();
      expect(screen.queryByText('summary_estimated_total')).not.toBeInTheDocument();
    });
  });

  describe('submission', () => {
    it('creates a reservation and navigates back on success', async () => {
      vi.mocked(reservationService.createReservation).mockResolvedValue(mockReservation());
      renderNew();
      await waitForTitle('new_reservation');
      completeNewFlow();
      fireEvent.click(screen.getByRole('button', { name: 'confirm_reservation' }));

      await waitFor(() => expect(reservationService.createReservation).toHaveBeenCalledWith(
        expect.objectContaining({ guestId: 'g1', checkInDate: '2026-04-01', checkOutDate: '2026-04-03' }),
      ));
      expect(mockNavigate).toHaveBeenCalledWith('/reservations');
    });

    it('sends only one request when Confirm is clicked twice in a row', async () => {
      vi.mocked(reservationService.createReservation).mockResolvedValue(mockReservation());
      renderNew();
      await waitForTitle('new_reservation');
      completeNewFlow();
      const confirm = screen.getByRole('button', { name: 'confirm_reservation' });
      fireEvent.click(confirm);
      fireEvent.click(confirm);

      await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/reservations'));
      expect(reservationService.createReservation).toHaveBeenCalledTimes(1);
    });

    it('updates an existing reservation from the summary panel in edit mode', async () => {
      vi.mocked(reservationService.getReservationById).mockResolvedValue(mockReservation({ id: 'res123', guestId: 'g1' }));
      vi.mocked(guestService.getGuestById).mockResolvedValue(mockGuest({ id: 'g1' }));
      vi.mocked(reservationService.updateReservation).mockResolvedValue(mockReservation());

      renderEdit();
      await waitForTitle('edit_reservation');
      fillEditReservation();
      fireEvent.click(screen.getByRole('button', { name: /update_reservation/i }));

      await waitFor(() => expect(reservationService.updateReservation).toHaveBeenCalledWith('res123', expect.anything()));
      expect(mockNavigate).toHaveBeenCalledWith('/reservations');
    });

    it('sends an edit with a missing room back to the rooms step', async () => {
      vi.mocked(reservationService.getReservationById).mockResolvedValue(mockReservation({ id: 'res123', guestId: 'g1' }));
      vi.mocked(guestService.getGuestById).mockResolvedValue(mockGuest({ id: 'g1' }));

      renderEdit();
      await waitForTitle('edit_reservation');
      fireEvent.click(screen.getByRole('button', { name: /update_reservation/i }));

      expect(await screen.findByText('msg_select_room')).toBeInTheDocument();
      expect(stepHeading('reservation_step_rooms')).toBeInTheDocument();
      expect(reservationService.updateReservation).not.toHaveBeenCalled();
    });

    it('echoes back the version read from the server on update (optimistic-lock check)', async () => {
      vi.mocked(reservationService.getReservationById).mockResolvedValue(
        mockReservation({ id: 'res123', guestId: 'g1', version: 7 }),
      );
      vi.mocked(guestService.getGuestById).mockResolvedValue(mockGuest({ id: 'g1' }));
      vi.mocked(reservationService.updateReservation).mockResolvedValue(mockReservation());

      renderEdit();
      await waitForTitle('edit_reservation');
      fillEditReservation();
      fireEvent.click(screen.getByRole('button', { name: /update_reservation/i }));

      await waitFor(() => expect(reservationService.updateReservation).toHaveBeenCalledWith(
        'res123', expect.objectContaining({ version: 7 }),
      ));
    });

    describe('stale-version conflict', () => {
      const openConflict = async () => {
        vi.mocked(reservationService.getReservationById).mockResolvedValue(
          mockReservation({ id: 'res123', guestId: 'g1', version: 3 }),
        );
        vi.mocked(guestService.getGuestById).mockResolvedValue(mockGuest({ id: 'g1' }));
        vi.mocked(reservationService.updateReservation).mockRejectedValue({
          response: { data: { errorCode: 'RESERVATION_STALE_VERSION' } },
        });

        renderEdit();
        await waitForTitle('edit_reservation');
        fillEditReservation();
        fireEvent.click(screen.getByRole('button', { name: /update_reservation/i }));
        await screen.findByText('reservation_stale_version_body');
      };

      it('shows a reload/cancel dialog on a stale-version 409, without navigating away', async () => {
        await openConflict();
        expect(mockNavigate).not.toHaveBeenCalled();
      });

      it('reloads the reservation when Refresh is clicked', async () => {
        vi.mocked(reservationService.getReservationById)
          .mockResolvedValueOnce(mockReservation({ id: 'res123', guestId: 'g1', version: 3 }))
          .mockResolvedValueOnce(mockReservation({ id: 'res123', guestId: 'g1', version: 4, checkInDate: '2026-06-01' }));
        await openConflict();

        fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'refresh' }));

        await waitFor(() => expect(reservationService.getReservationById).toHaveBeenCalledTimes(2));
        expect(screen.queryByText('reservation_stale_version_body')).not.toBeInTheDocument();
      });

      it('navigates back to the list when Cancel is clicked', async () => {
        await openConflict();
        fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'cancel' }));
        expect(mockNavigate).toHaveBeenCalledWith('/reservations');
      });
    });

    it('shows err_guest_not_found when the backend rejects with that error code', async () => {
      vi.mocked(reservationService.createReservation).mockRejectedValue({
        response: { data: { errorCode: 'GUEST_NOT_FOUND' } },
      });
      renderNew();
      await waitForTitle('new_reservation');
      completeNewFlow();
      fireEvent.click(screen.getByRole('button', { name: 'confirm_reservation' }));

      expect(await screen.findByText('err_guest_not_found')).toBeInTheDocument();
    });

    it('shows a generic failure message on a non-specific creation error', async () => {
      vi.mocked(reservationService.createReservation).mockRejectedValue({ response: { data: {} } });
      renderNew();
      await waitForTitle('new_reservation');
      completeNewFlow();
      fireEvent.click(screen.getByRole('button', { name: 'confirm_reservation' }));

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

      renderEdit();
      expect(await screen.findByText(/Fallback Guest/i)).toBeInTheDocument();
    });
  });

  it('navigates back to the list when the back button is clicked', async () => {
    renderNew();
    await waitForTitle('new_reservation');
    fireEvent.click(screen.getByLabelText('back'));
    expect(mockNavigate).toHaveBeenCalledWith('/reservations');
  });
});
