import { render, screen } from '@testing-library/react';
/* eslint-disable react-perf/jsx-no-new-array-as-prop -- test-only inputs, not the real render path */
import { describe, it, expect } from 'vitest';
import { ReservationSummary } from './ReservationSummary';
import type { GuestResponseDTO, RoomResponse } from '../../types';

const rooms = [
  { id: 'r1', roomNumber: '101', roomType: { name: 'Double' } },
  { id: 'r2', roomNumber: '102', type: 'SINGLE' },
] as unknown as RoomResponse[];

const guest = {
  id: 'g1',
  firstName: 'Mario',
  lastName: 'Rossi',
  email: 'mario@example.com',
  phone: '+39 333 1234567',
} as GuestResponseDTO;

const baseProps = {
  variant: 'full' as const,
  checkInDate: '2026-04-01',
  checkOutDate: '2026-04-04',
  expectedGuests: 2,
  rooms,
  selectedRoomIds: ['r1'],
  resolvedPrices: new Map<string, number>(),
  guest,
};

describe('ReservationSummary', () => {
  it('shows the stay, the chosen room and the guest', () => {
    render(<ReservationSummary {...baseProps} />);
    const region = screen.getByRole('region', { name: 'summary_title' });
    expect(region).toHaveTextContent('room_number');
    expect(region).toHaveTextContent('Double');
    expect(region).toHaveTextContent('Mario Rossi');
    expect(region).toHaveTextContent('summary_nights');
  });

  it('shows the guest contacts only in the full variant', () => {
    const { rerender } = render(<ReservationSummary {...baseProps} />);
    expect(screen.getByText(/mario@example.com/)).toBeInTheDocument();

    rerender(<ReservationSummary {...baseProps} variant="panel" />);
    expect(screen.queryByText(/mario@example.com/)).not.toBeInTheDocument();
  });

  it('falls back to the room type code when the type has no name', () => {
    render(<ReservationSummary {...baseProps} selectedRoomIds={['r2']} />);
    expect(screen.getByText(/SINGLE/)).toBeInTheDocument();
  });

  it('sums the resolved prices into an estimated total', () => {
    render(
      <ReservationSummary
        {...baseProps}
        selectedRoomIds={['r1', 'r2']}
        resolvedPrices={new Map([['r1', 100], ['r2', 50]])}
      />,
    );
    expect(screen.getByText(/150/)).toBeInTheDocument();
  });

  it('does not show a total when any room is missing a resolved price', () => {
    render(
      <ReservationSummary
        {...baseProps}
        selectedRoomIds={['r1', 'r2']}
        resolvedPrices={new Map([['r1', 100]])}
      />,
    );
    expect(screen.queryByText('summary_estimated_total')).not.toBeInTheDocument();
    expect(screen.getByText('summary_price_on_confirm')).toBeInTheDocument();
  });

  it('renders placeholders before anything is chosen', () => {
    render(
      <ReservationSummary
        {...baseProps}
        checkInDate=""
        checkOutDate=""
        expectedGuests=""
        selectedRoomIds={[]}
        guest={null}
      />,
    );
    expect(screen.getAllByText('—').length).toBeGreaterThan(0);
  });

  it('renders the actions passed as children', () => {
    render(
      <ReservationSummary {...baseProps} variant="panel">
        <button type="button">Save now</button>
      </ReservationSummary>,
    );
    expect(screen.getByRole('button', { name: 'Save now' })).toBeInTheDocument();
  });
});
