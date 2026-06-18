import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'vitest-axe';
import PlanningBoard from './PlanningBoard';
import type { RoomResponse } from '../types/inventory.types';
import type { ReservationResponse } from '../types/reservation.types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../components/m3/M3Card', () => ({
  M3Card: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

const ROOM_TYPE = {
  id: 'rt1', name: 'Single', maxOccupancy: 1, basePrice: 50,
  description: '', active: true, createdAt: '', updatedAt: '',
};

const ROOMS: RoomResponse[] = [
  { id: 'r1', hotelId: 'h1', roomNumber: '101', roomType: ROOM_TYPE, status: 'CLEAN', active: true, createdAt: '', updatedAt: '' },
  { id: 'r2', hotelId: 'h1', roomNumber: '202', roomType: ROOM_TYPE, status: 'DIRTY', active: true, createdAt: '', updatedAt: '' },
];

const RESERVATION: ReservationResponse = {
  id: 'res1', guestId: 'g1', guestFullName: 'John Doe', expectedGuests: 2, actualGuests: 0,
  checkInDate: '2026-05-01', checkOutDate: '2026-05-05', status: 'CONFIRMED',
  lineItems: [{ id: 'li1', roomId: 'r1', price: 50, active: true, createdAt: '', updatedAt: '' }],
  active: true, createdAt: '', updatedAt: '',
};

const CURRENT_DATE = new Date('2026-05-07');
const ON_NAVIGATE = vi.fn();
const EMPTY_RESERVATIONS: ReservationResponse[] = [];
const EMPTY_ROOMS: RoomResponse[] = [];
const SINGLE_RESERVATION = [RESERVATION];

describe('PlanningBoard', () => {
  it('renders room numbers in the sidebar', () => {
    render(
      <PlanningBoard
        rooms={ROOMS}
        reservations={EMPTY_RESERVATIONS}
        currentDate={CURRENT_DATE}
        onNavigate={ON_NAVIGATE}
      />
    );
    expect(screen.getByText('101')).toBeInTheDocument();
    expect(screen.getByText('202')).toBeInTheDocument();
  });

  it('renders room type names under each room number', () => {
    render(
      <PlanningBoard
        rooms={ROOMS}
        reservations={EMPTY_RESERVATIONS}
        currentDate={CURRENT_DATE}
        onNavigate={ON_NAVIGATE}
      />
    );
    expect(screen.getAllByText('Single').length).toBeGreaterThanOrEqual(1);
  });

  it('renders reservation guest name when reservation is present', () => {
    render(
      <PlanningBoard
        rooms={ROOMS}
        reservations={SINGLE_RESERVATION}
        currentDate={CURRENT_DATE}
        onNavigate={ON_NAVIGATE}
      />
    );
    expect(screen.getByText('John Doe')).toBeInTheDocument();
  });

  it('renders the nav_rooms label in the header', () => {
    render(
      <PlanningBoard
        rooms={ROOMS}
        reservations={EMPTY_RESERVATIONS}
        currentDate={CURRENT_DATE}
        onNavigate={ON_NAVIGATE}
      />
    );
    expect(screen.getByText('nav_rooms')).toBeInTheDocument();
  });

  it('renders with empty rooms array without crashing', () => {
    render(
      <PlanningBoard
        rooms={EMPTY_ROOMS}
        reservations={EMPTY_RESERVATIONS}
        currentDate={CURRENT_DATE}
        onNavigate={ON_NAVIGATE}
      />
    );
    expect(screen.getByText('nav_rooms')).toBeInTheDocument();
  });

  // Default 5000ms timeout is occasionally exceeded under --coverage (v8
  // instrumentation overhead on top of axe's full DOM tree walk).
  it('passes axe accessibility check', async () => {
    const { container } = render(
      <PlanningBoard
        rooms={ROOMS}
        reservations={EMPTY_RESERVATIONS}
        currentDate={CURRENT_DATE}
        onNavigate={ON_NAVIGATE}
      />
    );
    expect(await axe(container)).toHaveNoViolations();
  }, 15000);
});
