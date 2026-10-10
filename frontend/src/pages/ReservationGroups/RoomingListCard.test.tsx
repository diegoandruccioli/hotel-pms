import { render, screen, within, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { RoomingListCard } from './RoomingListCard';
import type { GroupMemberResponse, RoomResponse } from '../../types';

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

const member = (overrides: Partial<GroupMemberResponse> = {}): GroupMemberResponse => ({
  reservationId: 'r1', guestId: 'g1', guestFullName: 'Jane Doe', roomId: 'room1',
  expectedGuests: 2, actualGuests: 0, checkInDate: '2026-10-01', checkOutDate: '2026-10-03',
  status: 'CONFIRMED', billedToMasterFolio: false, price: 200,
  ...overrides,
});

const ROOMS = [
  { id: 'room1', roomNumber: '201', roomType: { name: 'Doppia' } },
] as unknown as RoomResponse[];

const MEMBERS = [
  member(),
  member({ reservationId: 'r2', guestId: 'g2', guestFullName: 'Mario Rossi', status: 'PENDING', roomId: 'room-unknown' }),
  member({ reservationId: 'r3', guestId: 'g3', guestFullName: 'Anna Serra', roomId: null }),
];

const renderCard = (members = MEMBERS, rooms: RoomResponse[] | undefined = ROOMS) =>
  render(<MemoryRouter><RoomingListCard members={members} rooms={rooms} /></MemoryRouter>);

const rowOf = (name: string) => screen.getByText(name).closest('tr')!;

describe('RoomingListCard', () => {
  it('shows room number and type from the lookup, and a dash when the room is unknown', () => {
    renderCard();
    expect(within(rowOf('Jane Doe')).getByText('201')).toBeInTheDocument();
    expect(within(rowOf('Jane Doe')).getByText('Doppia')).toBeInTheDocument();
    expect(within(rowOf('Mario Rossi')).getAllByText('—').length).toBeGreaterThanOrEqual(2);
  });

  it('falls back to dashes while the room lookup is empty or unavailable', () => {
    renderCard(MEMBERS, []);
    expect(within(rowOf('Jane Doe')).queryByText('201')).not.toBeInTheDocument();
  });

  it('shows the reservation status chip, and "unassigned" when there is no room', () => {
    renderCard();
    expect(within(rowOf('Jane Doe')).getByText(/^status_confirmed/)).toBeInTheDocument();
    expect(within(rowOf('Mario Rossi')).getByText(/^status_pending/)).toBeInTheDocument();
    expect(within(rowOf('Anna Serra')).getByText('rooming_unassigned')).toBeInTheDocument();
  });

  it('reports the progress in the header and in an accessible progressbar', () => {
    renderCard();
    expect(screen.getByText(/rooming_progress.*"ready":1.*"total":3/)).toBeInTheDocument();
    const bar = screen.getByRole('progressbar');
    expect(bar).toHaveAttribute('aria-valuenow', '1');
    expect(bar).toHaveAttribute('aria-valuemax', '3');
  });

  it('offers check-in only for confirmed members, with the reservation context', () => {
    renderCard();
    expect(within(rowOf('Mario Rossi')).queryByText('check_in')).not.toBeInTheDocument();
    fireEvent.click(within(rowOf('Jane Doe')).getByText('check_in'));
    expect(mockNavigate).toHaveBeenCalledWith('/stays/check-in/r1', {
      state: { roomId: 'room1', expectedGuests: 2, guestId: 'g1' },
    });
  });

  it('opens the reservation to view or edit it', () => {
    renderCard();
    fireEvent.click(within(rowOf('Jane Doe')).getByText('view'));
    expect(mockNavigate).toHaveBeenCalledWith('/reservations/r1');
    fireEvent.click(within(rowOf('Jane Doe')).getByText('edit'));
    expect(mockNavigate).toHaveBeenCalledWith('/reservations/edit/r1');
  });

  it('should have no accessibility violations', async () => {
    const { container } = renderCard();
    expect(await axe(container)).toHaveNoViolations();
  });
});
