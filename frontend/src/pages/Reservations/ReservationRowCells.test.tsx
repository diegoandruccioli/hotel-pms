import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { axe } from 'vitest-axe';
import type { ReservationResponse } from '../../types';
import { openRowMenu } from '../../test-utils';
import { ActionsCell, GuestNameCell } from './ReservationRowCells';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

describe('GuestNameCell', () => {
  it('shows the initial in a decorative avatar and the full name', () => {
    render(<GuestNameCell name="Marco Bianchi" />);
    expect(screen.getByText('Marco Bianchi')).toBeInTheDocument();
    expect(screen.getByText('M')).toHaveAttribute('aria-hidden', 'true');
  });

  it('falls back to a placeholder initial without a name', () => {
    render(<GuestNameCell />);
    expect(screen.getByText('?')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(<GuestNameCell name="Marco Bianchi" />);
    expect(await axe(container)).toHaveNoViolations();
  });
});

describe('GuestNameCell group link', () => {
  it('links a rooming-list member to its group', () => {
    render(<MemoryRouter><GuestNameCell name="Marco Bianchi" groupId="grp1" /></MemoryRouter>);
    expect(screen.getByRole('link', { name: 'reservation_group_link' })).toHaveAttribute('href', '/reservations/groups/grp1');
  });

  it('shows no group link for a standalone reservation', () => {
    render(<GuestNameCell name="Marco Bianchi" />);
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});

describe('ActionsCell', () => {
  const tKey = ((key: string) => key) as never;
  const noop = vi.fn();
  const reservation = (status: string) => ({
    id: 'r1', status, guestId: 'g1', expectedGuests: 1, checkInDate: '2099-01-01', lineItems: [],
  }) as unknown as ReservationResponse;
  const renderActions = (status: string) => render(
    <ActionsCell reservation={reservation(status)} onCheckIn={noop} onView={noop} onEdit={noop} t={tKey} />,
  );

  it('offers View and Edit on an open reservation', () => {
    renderActions('CONFIRMED');
    openRowMenu();
    expect(screen.getByRole('menuitem', { name: 'view' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'edit' })).toBeInTheDocument();
  });

  it.each(['CHECKED_OUT', 'CANCELLED', 'NO_SHOW'])('offers only View on a %s reservation', (status) => {
    renderActions(status);
    expect(screen.getByText('view')).toBeInTheDocument();
    expect(screen.queryByText('edit')).not.toBeInTheDocument();
  });
});
