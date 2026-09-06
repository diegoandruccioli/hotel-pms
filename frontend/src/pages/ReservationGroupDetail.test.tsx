import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { renderWithQuery as render } from '../test-utils';
import { ReservationGroupDetail } from './ReservationGroupDetail';
import { reservationGroupService } from '../services';

vi.mock('../services/reservationGroupService', () => ({
  reservationGroupService: {
    getGroup: vi.fn(),
    cancelGroup: vi.fn(),
    checkoutGroup: vi.fn(),
  },
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => (opts ? `${key} ${JSON.stringify(opts)}` : key),
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../store/toastStore', () => ({
  useToastStore: (sel: unknown) =>
    (sel as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

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
  members: [
    {
      reservationId: 'r1', guestId: 'g2', guestFullName: 'Jane Doe', roomId: 'room1',
      expectedGuests: 2, actualGuests: 0, checkInDate: '2026-10-01', checkOutDate: '2026-10-03',
      status: 'CONFIRMED', billedToMasterFolio: false, price: 200,
    },
  ],
  active: true,
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-01T00:00:00',
  version: 0,
};

const INITIAL_ENTRIES = ['/reservations/groups/group-1'];

const renderDetail = () => render(
  <MemoryRouter initialEntries={INITIAL_ENTRIES}>
    <Routes>
      <Route path="/reservations/groups/:id" element={<ReservationGroupDetail />} />
    </Routes>
  </MemoryRouter>,
);

describe('ReservationGroupDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render group details and rooming list', async () => {
    vi.mocked(reservationGroupService.getGroup).mockResolvedValueOnce(GROUP as never);
    renderDetail();

    await waitFor(() => expect(screen.getByText('Acme Corp Offsite')).toBeInTheDocument());
    expect(screen.getByText('Jane Doe')).toBeInTheDocument();
    expect(screen.getByText('Mario Rossi')).toBeInTheDocument();
  });

  it('should show error state on load failure', async () => {
    vi.mocked(reservationGroupService.getGroup).mockRejectedValueOnce(new Error('Network error'));
    renderDetail();

    await waitFor(() => expect(screen.getAllByText('group_load_failed').length).toBeGreaterThan(0));
  });

  it('should cancel the group when confirmed', async () => {
    vi.mocked(reservationGroupService.getGroup).mockResolvedValue(GROUP as never);
    vi.mocked(reservationGroupService.cancelGroup).mockResolvedValueOnce({ ...GROUP, status: 'CANCELLED' } as never);
    renderDetail();

    await waitFor(() => expect(screen.getByText('Acme Corp Offsite')).toBeInTheDocument());
    fireEvent.click(screen.getByText('cancel_group'));
    fireEvent.click(screen.getByText('confirm'));

    await waitFor(() => {
      expect(reservationGroupService.cancelGroup).toHaveBeenCalledWith('group-1', 0);
    });
  });

  it('should show an error toast when cancelling fails', async () => {
    vi.mocked(reservationGroupService.getGroup).mockResolvedValue(GROUP as never);
    vi.mocked(reservationGroupService.cancelGroup).mockRejectedValueOnce(new Error('Network error'));
    renderDetail();

    await waitFor(() => expect(screen.getByText('Acme Corp Offsite')).toBeInTheDocument());
    fireEvent.click(screen.getByText('cancel_group'));
    fireEvent.click(screen.getByText('confirm'));

    await waitFor(() => {
      expect(reservationGroupService.cancelGroup).toHaveBeenCalled();
    });
    // Dialog closes either way (finally block) -- confirm button no longer present.
    await waitFor(() => expect(screen.queryByText('confirm')).not.toBeInTheDocument());
  });

  it('should close the checkout dialog without confirming', async () => {
    const checkedInGroup = { ...GROUP, members: [{ ...GROUP.members[0], status: 'CHECKED_IN' }] };
    vi.mocked(reservationGroupService.getGroup).mockResolvedValue(checkedInGroup as never);
    renderDetail();

    await waitFor(() => expect(screen.getByText('Acme Corp Offsite')).toBeInTheDocument());
    fireEvent.click(screen.getByText('checkout_group'));
    await waitFor(() => expect(screen.getByText('checkout_group_confirm')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'cancel' }));

    await waitFor(() => expect(screen.queryByText('checkout_group_confirm')).not.toBeInTheDocument());
    expect(reservationGroupService.checkoutGroup).not.toHaveBeenCalled();
  });

  it('should show a partial-failure toast when some rooms fail to check out', async () => {
    const checkedInGroup = { ...GROUP, members: [{ ...GROUP.members[0], status: 'CHECKED_IN' }] };
    vi.mocked(reservationGroupService.getGroup).mockResolvedValue(checkedInGroup as never);
    vi.mocked(reservationGroupService.checkoutGroup).mockResolvedValueOnce([
      { reservationId: 'r1', stayId: 's1', success: false, errorCode: 'BILLING_NOT_PAID' },
    ] as never);
    renderDetail();

    await waitFor(() => expect(screen.getByText('Acme Corp Offsite')).toBeInTheDocument());
    fireEvent.click(screen.getByText('checkout_group'));
    fireEvent.click(screen.getByText('confirm'));

    expect(await screen.findByText('BILLING_NOT_PAID')).toBeInTheDocument();
  });

  it('should show an error toast when checkout fails outright', async () => {
    const checkedInGroup = { ...GROUP, members: [{ ...GROUP.members[0], status: 'CHECKED_IN' }] };
    vi.mocked(reservationGroupService.getGroup).mockResolvedValue(checkedInGroup as never);
    vi.mocked(reservationGroupService.checkoutGroup).mockRejectedValueOnce(new Error('Network error'));
    renderDetail();

    await waitFor(() => expect(screen.getByText('Acme Corp Offsite')).toBeInTheDocument());
    fireEvent.click(screen.getByText('checkout_group'));
    fireEvent.click(screen.getByText('confirm'));

    await waitFor(() => expect(screen.queryByText('checkout_group_confirm')).not.toBeInTheDocument());
  });

  it('should show the open master folio label when one exists', async () => {
    vi.mocked(reservationGroupService.getGroup).mockResolvedValueOnce(
      { ...GROUP, masterFolioInvoiceId: 'inv-1' } as never);
    renderDetail();

    await waitFor(() => expect(screen.getByText('master_folio_open')).toBeInTheDocument());
  });

  it('should check out the group and show per-room outcomes', async () => {
    const checkedInGroup = {
      ...GROUP,
      members: [{ ...GROUP.members[0], status: 'CHECKED_IN' }],
    };
    vi.mocked(reservationGroupService.getGroup).mockResolvedValue(checkedInGroup as never);
    vi.mocked(reservationGroupService.checkoutGroup).mockResolvedValueOnce([
      { reservationId: 'r1', stayId: 's1', success: true, errorCode: null },
    ] as never);
    renderDetail();

    await waitFor(() => expect(screen.getByText('Acme Corp Offsite')).toBeInTheDocument());
    fireEvent.click(screen.getByText('checkout_group'));
    fireEvent.click(screen.getByText('confirm'));

    await waitFor(() => {
      expect(reservationGroupService.checkoutGroup).toHaveBeenCalledWith('group-1');
    });
    expect(await screen.findByText('checkout_room_success')).toBeInTheDocument();
  });
});
