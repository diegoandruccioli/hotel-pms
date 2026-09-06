/* eslint-disable react-perf/jsx-no-new-function-as-prop -- test-only mock component, not the real perf-sensitive render path */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { renderWithQuery as render } from '../../test-utils';
import { ReservationGroupForm } from './ReservationGroupForm';
import { inventoryService } from '../../services';
import { reservationGroupService } from '../../services';

vi.mock('../../services/inventoryService');
vi.mock('../../services/reservationGroupService', () => ({
  reservationGroupService: { createGroup: vi.fn() },
}));

const mockNavigate = vi.hoisted(() => vi.fn());
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../store/toastStore', () => ({
  useToastStore: (sel: unknown) =>
    (sel as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

interface GuestMockProps {
  selectedGuest: { firstName: string; lastName: string } | null;
  onSelectGuest: (g: { id: string; firstName: string; lastName: string }) => void;
  onClearGuest: () => void;
}

function GuestSearchAndCreateMock({ selectedGuest, onSelectGuest }: GuestMockProps) {
  const handleSelect = () => onSelectGuest({ id: 'g1', firstName: 'Mario', lastName: 'Rossi' });
  return (
    <div>
      {selectedGuest ? `${selectedGuest.firstName} ${selectedGuest.lastName}` : (
        <button type="button" onClick={handleSelect}>select-guest</button>
      )}
    </div>
  );
}

vi.mock('../Reservations/GuestSearchAndCreate', () => ({
  GuestSearchAndCreate: (props: GuestMockProps) => GuestSearchAndCreateMock(props),
}));

const ROOM = { id: 'room1', roomNumber: '101', roomType: { id: 'rt1', name: 'Standard' } };

describe('ReservationGroupForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(inventoryService.getAvailableRooms).mockResolvedValue([ROOM] as never);
  });

  it('should submit a new group with one room', async () => {
    vi.mocked(reservationGroupService.createGroup).mockResolvedValueOnce({ id: 'group-1' } as never);
    render(<MemoryRouter><ReservationGroupForm /></MemoryRouter>);

    fireEvent.change(screen.getByLabelText('group_name'), { target: { value: 'Acme Corp Offsite' } });
    fireEvent.change(screen.getByLabelText('label_checkin_date'), { target: { value: '2026-10-01' } });
    fireEvent.change(screen.getByLabelText('label_checkout_date'), { target: { value: '2026-10-03' } });

    const selectGuestButtons = screen.getAllByText('select-guest');
    fireEvent.click(selectGuestButtons[0]); // contact guest

    await waitFor(() => expect(screen.getAllByText('select-guest')).toHaveLength(1));
    fireEvent.click(screen.getByText('select-guest')); // room guest

    await waitFor(() => {
      expect(screen.getByLabelText(/^label_room/)).toBeInTheDocument();
    });
    fireEvent.change(screen.getByLabelText(/^label_room/), { target: { value: 'room1' } });

    await waitFor(() => expect(screen.getByText('create_group')).not.toBeDisabled());
    fireEvent.click(screen.getByText('create_group'));

    await waitFor(() => {
      expect(reservationGroupService.createGroup).toHaveBeenCalledWith(expect.objectContaining({
        name: 'Acme Corp Offsite',
        contactGuestId: 'g1',
        checkInDate: '2026-10-01',
        checkOutDate: '2026-10-03',
        rooms: [expect.objectContaining({ guestId: 'g1', roomId: 'room1', expectedGuests: 1 })],
      }));
    });
    expect(mockNavigate).toHaveBeenCalledWith('/reservations/groups/group-1');
  });

  it('should update companyName/groupRatePerNight/notes/openMasterFolio and add/remove a room row', async () => {
    render(<MemoryRouter><ReservationGroupForm /></MemoryRouter>);

    fireEvent.change(screen.getByLabelText('company_name'), { target: { value: 'Acme Corp' } });
    fireEvent.change(screen.getByLabelText(/group_rate_per_night/), { target: { value: '150' } });
    fireEvent.change(screen.getByLabelText('notes'), { target: { value: 'VIP group' } });
    fireEvent.click(screen.getByLabelText('open_master_folio'));

    expect(screen.getByLabelText('company_name')).toHaveValue('Acme Corp');
    expect(screen.getByLabelText('notes')).toHaveValue('VIP group');

    // Adding a room row renders a second rooming-list guest picker.
    fireEvent.click(screen.getByText('add_room'));
    await waitFor(() => expect(screen.getAllByRole('button', { name: 'remove_room' })).toHaveLength(2));

    // Removing one row goes back to a single row without a remove button (last row can't be removed).
    fireEvent.click(screen.getAllByRole('button', { name: 'remove_room' })[0]);
    await waitFor(() => expect(screen.queryByRole('button', { name: 'remove_room' })).not.toBeInTheDocument());
  });

  it('should navigate back to the list when cancel is clicked', () => {
    render(<MemoryRouter><ReservationGroupForm /></MemoryRouter>);
    fireEvent.click(screen.getByText('cancel'));
    expect(mockNavigate).toHaveBeenCalledWith('/reservations/groups');
  });
});
