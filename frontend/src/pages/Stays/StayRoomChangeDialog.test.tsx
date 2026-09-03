import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { StayRoomChangeDialog } from './StayRoomChangeDialog';
import { stayService } from '../../services';
import { inventoryService } from '../../services';
import type { StayResponse } from '../../types';
import type { RoomResponse } from '../../types';

vi.mock('react-i18next', () => {
  const t = (key: string, opts?: Record<string, unknown>) =>
    opts ? `${key}:${JSON.stringify(opts)}` : key;
  return {
    useTranslation: () => ({ t }),
    initReactI18next: { type: '3rdParty', init: vi.fn() },
  };
});

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('../../services/stayService', () => ({
  stayService: {
    changeRoom: vi.fn(),
  },
}));

vi.mock('../../services/inventoryService', () => ({
  inventoryService: {
    getAvailableRooms: vi.fn(),
  },
}));

const addToastMock = vi.hoisted(() => vi.fn());
vi.mock('../../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: typeof addToastMock }) => unknown)({ addToast: addToastMock }),
}));

const roomType = (maxOccupancy: number) => ({
  id: `type-${maxOccupancy}`,
  name: 'Standard',
  maxOccupancy,
  basePrice: 90,
  active: true,
  createdAt: '2026-05-01T00:00:00Z',
  updatedAt: '2026-05-01T00:00:00Z',
});

// Computed relative to the real system clock, not hardcoded, so this fixture
// never drifts into the past (the dialog itself rejects an overdue checkout —
// see the dedicated test for that).
const FUTURE_CHECKOUT_DATE = new Date(Date.now() + 10 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

const room = (id: string, roomNumber: string, maxOccupancy: number): RoomResponse => ({
  id,
  hotelId: 'hotel-1',
  roomNumber,
  roomType: roomType(maxOccupancy),
  status: 'CLEAN',
  active: true,
  createdAt: '2026-05-01T00:00:00Z',
  updatedAt: '2026-05-01T00:00:00Z',
});

const baseStay: StayResponse = {
  id: 'stay-1',
  reservationId: 'res-1',
  guestId: 'guest-1',
  roomId: 'room-101',
  roomNumber: '101',
  status: 'CHECKED_IN',
  createdAt: '2026-05-01T10:00:00Z',
  updatedAt: '2026-05-01T10:00:00Z',
  alloggiatiSent: true,
  alloggiatiSendFailed: false,
  expectedCheckOutDate: FUTURE_CHECKOUT_DATE,
  version: 3,
  guests: [
    { id: 'g1', firstName: 'Mario', lastName: 'Rossi', gender: '1', dateOfBirth: '1990-01-01',
      placeOfBirth: '058091000', citizenship: '100000100', isPrimaryGuest: true,
      travellerType: 'CAPOFAMIGLIA', arrivalDate: '2026-05-01', alloggiatiSent: true, needsResubmit: false },
  ],
  invoiceCreationFailed: false,
  checkoutEmailFailed: false,
};

const overdueStay: StayResponse = { ...baseStay, expectedCheckOutDate: '2000-01-01' };

describe('StayRoomChangeDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders nothing when stay is null', () => {
    const { container } = render(
      <StayRoomChangeDialog stay={null} onClose={vi.fn()} onChanged={vi.fn()} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('shows an actionable error and never calls the availability search when checkout is already in the past', async () => {
    render(<StayRoomChangeDialog stay={overdueStay} onClose={vi.fn()} onChanged={vi.fn()} />);

    await screen.findByText('errors:STAY_ROOM_CHANGE_CHECKOUT_IN_PAST');
    expect(inventoryService.getAvailableRooms).not.toHaveBeenCalled();
  });

  it('excludes the current room, undersized rooms, and non-CLEAN rooms from the picker', async () => {
    vi.mocked(inventoryService.getAvailableRooms).mockResolvedValue([
      room('room-101', '101', 4), // current room — must be excluded regardless of size
      room('room-102', '102', 0), // capacity 0 < 1 active guest — must be excluded
      // the availability endpoint deliberately includes DIRTY rooms for a
      // future-dated search, but an immediate room move has no cleaning-window
      // buffer — must still be excluded from this picker.
      { ...room('room-303', '303', 2), status: 'DIRTY' as const },
      room('room-205', '205', 2), // valid destination
    ]);

    render(<StayRoomChangeDialog stay={baseStay} onClose={vi.fn()} onChanged={vi.fn()} />);

    await waitFor(() => {
      expect(inventoryService.getAvailableRooms).toHaveBeenCalledWith(expect.any(String), FUTURE_CHECKOUT_DATE);
    });

    const select = await screen.findByLabelText(/stay_room_change_select_room/);
    await waitFor(() => {
      expect(select).toHaveTextContent('205');
    });
    expect(select).not.toHaveTextContent('101 — Standard');
    expect(select).not.toHaveTextContent('102 — Standard');
    expect(select).not.toHaveTextContent('303 — Standard');
  });

  it('submits the selected room and the stay version, then reports success', async () => {
    vi.mocked(inventoryService.getAvailableRooms).mockResolvedValue([room('room-205', '205', 2)]);
    vi.mocked(stayService.changeRoom).mockResolvedValue({ ...baseStay, roomId: 'room-205', roomNumber: '205' });
    const onChanged = vi.fn();
    const onClose = vi.fn();

    render(<StayRoomChangeDialog stay={baseStay} onClose={onClose} onChanged={onChanged} />);

    const select = await screen.findByLabelText(/stay_room_change_select_room/);
    await waitFor(() => expect(select).toHaveTextContent('205'));
    fireEvent.change(select, { target: { value: 'room-205' } });

    fireEvent.click(screen.getByText('confirm'));

    await waitFor(() => {
      expect(stayService.changeRoom).toHaveBeenCalledWith('stay-1', 'room-205', 3);
    });
    expect(addToastMock).toHaveBeenCalledWith('stay_room_change_success', 'success');
    expect(onChanged).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('shows an inline error and does not close on failure', async () => {
    vi.mocked(inventoryService.getAvailableRooms).mockResolvedValue([room('room-205', '205', 2)]);
    vi.mocked(stayService.changeRoom).mockRejectedValue(new Error('boom'));
    const onClose = vi.fn();

    render(<StayRoomChangeDialog stay={baseStay} onClose={onClose} onChanged={vi.fn()} />);

    const select = await screen.findByLabelText(/stay_room_change_select_room/);
    await waitFor(() => expect(select).toHaveTextContent('205'));
    fireEvent.change(select, { target: { value: 'room-205' } });
    fireEvent.click(screen.getByText('confirm'));

    await screen.findByText('stay_room_change_failed');
    expect(onClose).not.toHaveBeenCalled();
  });
});
