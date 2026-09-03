import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { RoomSelection } from './RoomSelection';
import type { RoomResponse } from '../../types';
import type { ReservationResponse } from '../../types';

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
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const ROOM_TYPE = { id: 'rt1', name: 'Standard', maxOccupancy: 2, basePrice: 90, active: true, createdAt: '', updatedAt: '' };

const ROOM_101: RoomResponse = {
  id: 'r1', hotelId: 'h1', roomNumber: '101', roomType: ROOM_TYPE,
  status: 'CLEAN', active: true, createdAt: '', updatedAt: '',
};
const ROOM_102: RoomResponse = {
  id: 'r2', hotelId: 'h1', roomNumber: '102', roomType: { ...ROOM_TYPE, basePrice: 100 },
  status: 'CLEAN', active: true, createdAt: '', updatedAt: '',
};
const ROOMS = [ROOM_101, ROOM_102];
const NO_ROOMS: RoomResponse[] = [];
const EMPTY_IDS: string[] = [];
const SELECTED_R1 = ['r1'];
const NO_RESERVATIONS: ReservationResponse[] = [];

const RESERVATION_OVERLAPPING_R1: ReservationResponse = {
  id: 'res-other',
  guestId: 'g1',
  checkInDate: '2026-08-01',
  checkOutDate: '2026-08-05',
  status: 'CONFIRMED',
  expectedGuests: 2,
  lineItems: [{ id: 'li1', roomId: 'r1', price: 90, active: true, createdAt: '', updatedAt: '' }],
  active: true,
  createdAt: '',
  updatedAt: '',
  confirmationEmailFailed: false,
};
const RESERVATIONS_R1_OCCUPIED = [RESERVATION_OVERLAPPING_R1];

const RESERVATION_IS_CURRENT: ReservationResponse = { ...RESERVATION_OVERLAPPING_R1, id: 'res-current' };
const RESERVATIONS_CURRENT_ONLY = [RESERVATION_IS_CURRENT];

const noop = () => { /* not under test */ };

describe('RoomSelection', () => {
  it('renders the date/guests fields and the room grid', () => {
    render(
      <RoomSelection
        checkInDate="2026-08-01"
        checkOutDate="2026-08-05"
        expectedGuests={2}
        availableRooms={ROOMS}
        selectedRoomIds={EMPTY_IDS}
        allReservations={NO_RESERVATIONS}
        onCheckInChange={noop}
        onCheckOutChange={noop}
        onExpectedGuestsChange={noop}
        onToggleRoom={noop}
      />,
    );
    expect(screen.getByLabelText('label_checkin_date')).toHaveValue('2026-08-01');
    expect(screen.getByLabelText('label_checkout_date')).toHaveValue('2026-08-05');
    expect(screen.getByLabelText('label_expected_guests')).toHaveValue(2);
    expect(screen.getAllByText('common:room_number')).toHaveLength(2);
  });

  it('shows the empty state when there are no available rooms', () => {
    render(
      <RoomSelection
        checkInDate="2026-08-01"
        checkOutDate="2026-08-05"
        expectedGuests={2}
        availableRooms={NO_ROOMS}
        selectedRoomIds={EMPTY_IDS}
        allReservations={NO_RESERVATIONS}
        onCheckInChange={noop}
        onCheckOutChange={noop}
        onExpectedGuestsChange={noop}
        onToggleRoom={noop}
      />,
    );
    expect(screen.getByText('no_rooms_available')).toBeInTheDocument();
  });

  it('calls onToggleRoom when a free room is clicked', () => {
    const onToggleRoom = vi.fn();
    render(
      <RoomSelection
        checkInDate="2026-08-01"
        checkOutDate="2026-08-05"
        expectedGuests={2}
        availableRooms={ROOMS}
        selectedRoomIds={EMPTY_IDS}
        allReservations={NO_RESERVATIONS}
        onCheckInChange={noop}
        onCheckOutChange={noop}
        onExpectedGuestsChange={noop}
        onToggleRoom={onToggleRoom}
      />,
    );
    fireEvent.click(screen.getAllByText('common:room_number')[0]);
    expect(onToggleRoom).toHaveBeenCalledWith('r1');
  });

  it('marks a room occupied when it overlaps another active reservation, and blocks the click', () => {
    const onToggleRoom = vi.fn();
    render(
      <RoomSelection
        checkInDate="2026-08-01"
        checkOutDate="2026-08-05"
        expectedGuests={2}
        availableRooms={ROOMS}
        selectedRoomIds={EMPTY_IDS}
        allReservations={RESERVATIONS_R1_OCCUPIED}
        onCheckInChange={noop}
        onCheckOutChange={noop}
        onExpectedGuestsChange={noop}
        onToggleRoom={onToggleRoom}
      />,
    );
    expect(screen.getByText('common:room_occupied')).toBeInTheDocument();
    fireEvent.click(screen.getAllByText('common:room_number')[0]);
    expect(onToggleRoom).not.toHaveBeenCalled();
  });

  it('does not treat the current reservation itself as an occupying conflict', () => {
    render(
      <RoomSelection
        checkInDate="2026-08-01"
        checkOutDate="2026-08-05"
        expectedGuests={2}
        availableRooms={ROOMS}
        selectedRoomIds={EMPTY_IDS}
        allReservations={RESERVATIONS_CURRENT_ONLY}
        currentReservationId="res-current"
        onCheckInChange={noop}
        onCheckOutChange={noop}
        onExpectedGuestsChange={noop}
        onToggleRoom={noop}
      />,
    );
    expect(screen.queryByText('common:room_occupied')).not.toBeInTheDocument();
  });

  it('marks the room as selected when its id is in selectedRoomIds', () => {
    render(
      <RoomSelection
        checkInDate="2026-08-01"
        checkOutDate="2026-08-05"
        expectedGuests={2}
        availableRooms={ROOMS}
        selectedRoomIds={SELECTED_R1}
        allReservations={NO_RESERVATIONS}
        onCheckInChange={noop}
        onCheckOutChange={noop}
        onExpectedGuestsChange={noop}
        onToggleRoom={noop}
      />,
    );
    const roomButtons = screen.getAllByText('common:room_number').map((el) => el.closest('button'));
    expect(roomButtons[0]).toHaveClass('border-primary');
    expect(roomButtons[1]).not.toHaveClass('border-primary');
  });

  it('does not call onToggleRoom for a free room when readOnly', () => {
    const onToggleRoom = vi.fn();
    render(
      <RoomSelection
        checkInDate="2026-08-01"
        checkOutDate="2026-08-05"
        expectedGuests={2}
        availableRooms={ROOMS}
        selectedRoomIds={EMPTY_IDS}
        allReservations={NO_RESERVATIONS}
        onCheckInChange={noop}
        onCheckOutChange={noop}
        onExpectedGuestsChange={noop}
        onToggleRoom={onToggleRoom}
        readOnly
      />,
    );
    expect(screen.getByLabelText('label_checkin_date')).toHaveAttribute('readonly');
    fireEvent.click(screen.getAllByText('common:room_number')[0]);
    expect(onToggleRoom).not.toHaveBeenCalled();
  });

  it('propagates date and guest count changes', () => {
    const onCheckInChange = vi.fn();
    const onExpectedGuestsChange = vi.fn();
    render(
      <RoomSelection
        checkInDate="2026-08-01"
        checkOutDate="2026-08-05"
        expectedGuests={2}
        availableRooms={ROOMS}
        selectedRoomIds={EMPTY_IDS}
        allReservations={NO_RESERVATIONS}
        onCheckInChange={onCheckInChange}
        onCheckOutChange={noop}
        onExpectedGuestsChange={onExpectedGuestsChange}
        onToggleRoom={noop}
      />,
    );
    fireEvent.change(screen.getByLabelText('label_checkin_date'), { target: { value: '2026-08-02' } });
    expect(onCheckInChange).toHaveBeenCalledWith('2026-08-02');

    fireEvent.change(screen.getByLabelText('label_expected_guests'), { target: { value: '4' } });
    expect(onExpectedGuestsChange).toHaveBeenCalledWith(4);
  });

  it('shows the flat basePrice when no resolved price is available for the room', () => {
    render(
      <RoomSelection
        checkInDate="2026-08-01"
        checkOutDate="2026-08-05"
        expectedGuests={2}
        availableRooms={ROOMS}
        selectedRoomIds={EMPTY_IDS}
        allReservations={NO_RESERVATIONS}
        onCheckInChange={noop}
        onCheckOutChange={noop}
        onExpectedGuestsChange={noop}
        onToggleRoom={noop}
      />,
    );
    expect(screen.getByText('€90')).toBeInTheDocument();
  });

  it('shows the date-aware resolved total price when available for the room', () => {
    const resolvedPrices = new Map([['r1', 360]]);
    render(
      <RoomSelection
        checkInDate="2026-08-01"
        checkOutDate="2026-08-05"
        expectedGuests={2}
        availableRooms={ROOMS}
        selectedRoomIds={EMPTY_IDS}
        allReservations={NO_RESERVATIONS}
        resolvedPrices={resolvedPrices}
        onCheckInChange={noop}
        onCheckOutChange={noop}
        onExpectedGuestsChange={noop}
        onToggleRoom={noop}
      />,
    );
    expect(screen.getByText('reservations:price_total_stay')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <RoomSelection
        checkInDate="2026-08-01"
        checkOutDate="2026-08-05"
        expectedGuests={2}
        availableRooms={ROOMS}
        selectedRoomIds={EMPTY_IDS}
        allReservations={NO_RESERVATIONS}
        onCheckInChange={noop}
        onCheckOutChange={noop}
        onExpectedGuestsChange={noop}
        onToggleRoom={noop}
      />,
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
