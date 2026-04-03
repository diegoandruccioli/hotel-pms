import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
/* eslint-disable react-perf/jsx-no-new-array-as-prop */
import { describe, it, expect, vi, beforeEach } from 'vitest';
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

vi.mock('./GuestSearchAndCreate', () => ({
  GuestSearchAndCreate: ({ selectedGuest, readOnly }: { selectedGuest: { firstName: string; lastName: string } | null; readOnly?: boolean }) => (
    <div data-testid="guest-mock">
      {selectedGuest ? `${selectedGuest.firstName} ${selectedGuest.lastName}` : 'No Guest'}
      {readOnly && <span>Read Only</span>}
    </div>
  )
}));

vi.mock('./RoomSelection', () => ({
  RoomSelection: ({ readOnly }: { readOnly?: boolean }) => (
    <div data-testid="room-mock">
      Room Mock
      {readOnly && <span>Read Only</span>}
    </div>
  )
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
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
});
