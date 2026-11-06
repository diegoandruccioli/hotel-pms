import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { ReservationGroupLink } from './ReservationGroupLink';
import { useReservationGroup } from '../../hooks/queries';

vi.mock('../../hooks/queries', () => ({ useReservationGroup: vi.fn() }));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: { name?: string }) => (opts?.name ? `${key}:${opts.name}` : key),
  }),
}));

const renderLink = () => render(<MemoryRouter><ReservationGroupLink groupId="grp1" /></MemoryRouter>);

describe('ReservationGroupLink', () => {
  beforeEach(() => vi.clearAllMocks());

  it('links to the group and names it once loaded', () => {
    vi.mocked(useReservationGroup).mockReturnValue({ data: { name: 'Congresso ACME' } } as never);
    renderLink();
    const link = screen.getByRole('link', { name: 'reservation_part_of_group:Congresso ACME' });
    expect(link).toHaveAttribute('href', '/reservations/groups/grp1');
  });

  it('stays usable with a generic label while the group loads or when it cannot be read', () => {
    vi.mocked(useReservationGroup).mockReturnValue({ data: undefined } as never);
    renderLink();
    expect(screen.getByRole('link', { name: 'reservation_group_link' })).toHaveAttribute('href', '/reservations/groups/grp1');
  });

  it('has no accessibility violations', async () => {
    vi.mocked(useReservationGroup).mockReturnValue({ data: { name: 'Congresso ACME' } } as never);
    const { container } = renderLink();
    expect(await axe(container)).toHaveNoViolations();
  });
});
