import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { renderWithQuery as render } from '../test-utils';

const mockNavigate = vi.hoisted(() => vi.fn());
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

import { ReservationGroups } from './ReservationGroups';
import { reservationGroupService } from '../services';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string, opts?: Record<string, unknown>) => (opts ? `${key} ${JSON.stringify(opts)}` : key) }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/reservationGroupService', () => ({
  reservationGroupService: { getAllGroups: vi.fn() },
}));

const page = (content: unknown[], totalPages = 1) => ({ content, totalPages, totalElements: content.length });

const GROUP = {
  id: 'group-1',
  name: 'Acme Corp Offsite',
  companyName: 'Acme Corp',
  contactGuestId: 'g1',
  contactGuestName: 'Mario Rossi',
  checkInDate: '2026-10-01',
  checkOutDate: '2026-10-03',
  status: 'CONFIRMED',
  groupRatePerNight: 100,
  masterFolioInvoiceId: null,
  notes: null,
  members: [{ reservationId: 'r1' }, { reservationId: 'r2' }],
  active: true,
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
  version: 0,
};

describe('ReservationGroups', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should show loading spinner initially', () => {
    vi.mocked(reservationGroupService.getAllGroups).mockReturnValue(new Promise(() => {}));
    render(<MemoryRouter><ReservationGroups /></MemoryRouter>);
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should render groups on success', async () => {
    vi.mocked(reservationGroupService.getAllGroups).mockResolvedValueOnce(page([GROUP]) as never);
    render(<MemoryRouter><ReservationGroups /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('Acme Corp Offsite')).toBeInTheDocument());
    expect(screen.getByText('Acme Corp')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('should show empty state when no groups', async () => {
    vi.mocked(reservationGroupService.getAllGroups).mockResolvedValueOnce(page([]) as never);
    render(<MemoryRouter><ReservationGroups /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('no_groups_found')).toBeInTheDocument());
  });

  it('should show error on failure', async () => {
    vi.mocked(reservationGroupService.getAllGroups).mockRejectedValueOnce(new Error('Network error'));
    render(<MemoryRouter><ReservationGroups /></MemoryRouter>);

    await waitFor(() => expect(screen.getAllByText('groups_load_failed').length).toBeGreaterThan(0));
  });

  it('should navigate to the new-group form when the button is clicked', async () => {
    vi.mocked(reservationGroupService.getAllGroups).mockResolvedValueOnce(page([]) as never);
    render(<MemoryRouter><ReservationGroups /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('no_groups_found')).toBeInTheDocument());
    fireEvent.click(screen.getByText('new_group'));

    expect(mockNavigate).toHaveBeenCalledWith('/reservations/groups/new');
  });

  it('should navigate to the group detail page when a group name is clicked', async () => {
    vi.mocked(reservationGroupService.getAllGroups).mockResolvedValueOnce(page([GROUP]) as never);
    render(<MemoryRouter><ReservationGroups /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('Acme Corp Offsite')).toBeInTheDocument());
    fireEvent.click(screen.getByText('Acme Corp Offsite'));

    expect(mockNavigate).toHaveBeenCalledWith('/reservations/groups/group-1');
  });

  it('should render every group status tone', async () => {
    const groups = [
      { ...GROUP, id: 'g-out', name: 'Checked Out Group', status: 'CHECKED_OUT' },
      { ...GROUP, id: 'g-in', name: 'Checked In Group', status: 'CHECKED_IN' },
      { ...GROUP, id: 'g-cancel', name: 'Cancelled Group', status: 'CANCELLED' },
    ];
    vi.mocked(reservationGroupService.getAllGroups).mockResolvedValueOnce(page(groups) as never);
    render(<MemoryRouter><ReservationGroups /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('Checked Out Group')).toBeInTheDocument());
    expect(screen.getByText('Checked In Group')).toBeInTheDocument();
    expect(screen.getByText('Cancelled Group')).toBeInTheDocument();
  });

  it('should page forward and back', async () => {
    vi.mocked(reservationGroupService.getAllGroups)
      .mockResolvedValueOnce(page([GROUP], 2) as never)
      .mockResolvedValueOnce(page([GROUP], 2) as never);
    render(<MemoryRouter><ReservationGroups /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText('Acme Corp Offsite')).toBeInTheDocument());
    fireEvent.click(screen.getByText('next_page'));

    await waitFor(() => {
      expect(reservationGroupService.getAllGroups).toHaveBeenLastCalledWith(1, 20);
    });

    fireEvent.click(screen.getByText('prev_page'));
    await waitFor(() => {
      expect(reservationGroupService.getAllGroups).toHaveBeenLastCalledWith(0, 20);
    });
  });
});
