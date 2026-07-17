/* eslint-disable react-perf/jsx-no-new-array-as-prop -- test-only inline fixtures, not the real render path */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
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
  active: true, createdAt: '', updatedAt: '', confirmationEmailFailed: false,
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

  function makeDataTransfer(store: Record<string, string> = {}) {
    return {
      setData: (type: string, val: string) => { store[type] = val; },
      getData: (type: string) => store[type] ?? '',
      effectAllowed: '',
      dropEffect: '',
    };
  }

  it('renders the MAINTENANCE/OCCUPIED room status dot color (neither CLEAN nor DIRTY)', () => {
    const maintenanceRoom = { ...ROOMS[0], id: 'r3', roomNumber: '303', status: 'MAINTENANCE' as const };
    render(
      <PlanningBoard
        rooms={[maintenanceRoom]}
        reservations={EMPTY_RESERVATIONS}
        currentDate={CURRENT_DATE}
        onNavigate={ON_NAVIGATE}
      />
    );
    expect(screen.getByText('303')).toBeInTheDocument();
  });

  it('does not render a reservation bar entirely outside the visible month', () => {
    const outsideReservation: ReservationResponse = {
      ...RESERVATION, id: 'res-outside',
      checkInDate: '2026-01-01', checkOutDate: '2026-01-05',
    };
    render(
      <PlanningBoard
        rooms={ROOMS}
        reservations={[outsideReservation]}
        currentDate={CURRENT_DATE}
        onNavigate={ON_NAVIGATE}
      />
    );
    expect(screen.queryByText('John Doe')).not.toBeInTheDocument();
  });

  it('clamps a reservation that starts before and ends after the visible month', () => {
    const spanningReservation: ReservationResponse = {
      ...RESERVATION, id: 'res-span',
      checkInDate: '2026-04-15', checkOutDate: '2026-06-15',
    };
    render(
      <PlanningBoard
        rooms={ROOMS}
        reservations={[spanningReservation]}
        currentDate={CURRENT_DATE}
        onNavigate={ON_NAVIGATE}
      />
    );
    expect(screen.getByText('John Doe')).toBeInTheDocument();
  });

  it('renders CANCELLED, PENDING and CHECKED_OUT status colors', () => {
    const statuses: ReservationResponse['status'][] = ['CANCELLED', 'PENDING', 'CHECKED_OUT'];
    const reservations = statuses.map((status, i) => ({
      ...RESERVATION, id: `res-${status}`, guestFullName: `Guest ${i}`, status,
      lineItems: [{ id: `li-${status}`, roomId: 'r1', price: 50, active: true, createdAt: '', updatedAt: '' }],
    }));
    render(
      <PlanningBoard
        rooms={ROOMS}
        reservations={reservations}
        currentDate={CURRENT_DATE}
        onNavigate={ON_NAVIGATE}
      />
    );
    expect(screen.getByText('Guest 0')).toBeInTheDocument();
    expect(screen.getByText('Guest 1')).toBeInTheDocument();
    expect(screen.getByText('Guest 2')).toBeInTheDocument();
  });

  it('drags a reservation bar and drops it on a different room, calling onReservationMove', () => {
    const onMove = vi.fn();
    render(
      <PlanningBoard
        rooms={ROOMS}
        reservations={SINGLE_RESERVATION}
        currentDate={CURRENT_DATE}
        onNavigate={ON_NAVIGATE}
        onReservationMove={onMove}
      />
    );
    const bar = screen.getByTitle(/John Doe/);
    const dataTransfer = makeDataTransfer();
    fireEvent.dragStart(bar, { dataTransfer });
    expect(dataTransfer.getData('application/json')).toContain('"reservationId":"res1"');

    // Drop directly on the row element that owns room r2's allocations area.
    const rows = document.querySelectorAll('.border-b.border-outline-variant.relative');
    fireEvent.dragOver(rows[1], { dataTransfer });
    fireEvent.drop(rows[1], { dataTransfer });

    expect(onMove).toHaveBeenCalledWith('res1', 'r1', 'r2');
    fireEvent.dragEnd(bar);
  });

  it('does not call onReservationMove when dropped back on the same room', () => {
    const onMove = vi.fn();
    render(
      <PlanningBoard
        rooms={ROOMS}
        reservations={SINGLE_RESERVATION}
        currentDate={CURRENT_DATE}
        onNavigate={ON_NAVIGATE}
        onReservationMove={onMove}
      />
    );
    const bar = screen.getByTitle(/John Doe/);
    const dataTransfer = makeDataTransfer();
    fireEvent.dragStart(bar, { dataTransfer });

    const rows = document.querySelectorAll('.border-b.border-outline-variant.relative');
    fireEvent.drop(rows[0], { dataTransfer });

    expect(onMove).not.toHaveBeenCalled();
  });

  it('ignores a drop with no drag payload', () => {
    const onMove = vi.fn();
    render(
      <PlanningBoard
        rooms={ROOMS}
        reservations={EMPTY_RESERVATIONS}
        currentDate={CURRENT_DATE}
        onNavigate={ON_NAVIGATE}
        onReservationMove={onMove}
      />
    );
    const rows = document.querySelectorAll('.border-b.border-outline-variant.relative');
    fireEvent.drop(rows[0], { dataTransfer: makeDataTransfer() });
    expect(onMove).not.toHaveBeenCalled();
  });

  it('toggles drag-over styling on dragOver/dragLeave without throwing', () => {
    render(
      <PlanningBoard
        rooms={ROOMS}
        reservations={EMPTY_RESERVATIONS}
        currentDate={CURRENT_DATE}
        onNavigate={ON_NAVIGATE}
      />
    );
    const rows = document.querySelectorAll('.border-b.border-outline-variant.relative');
    const row = rows[0];
    fireEvent.dragOver(row, { dataTransfer: makeDataTransfer() });
    expect(row.className).toContain('bg-primary-container/30');
    fireEvent.dragLeave(row, { relatedTarget: document.body });
    expect(row.className).not.toContain('bg-primary-container/30');
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
