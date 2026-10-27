/* eslint-disable react-perf/jsx-no-new-array-as-prop */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { renderWithQuery as render } from '../test-utils';
import { StayDetail } from './StayDetail';
import { stayService } from '../services';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/stayService', () => ({
  stayService: {
    getStayById: vi.fn(),
    checkOut: vi.fn(),
    retryInvoiceCreation: vi.fn(),
    retryCheckoutEmail: vi.fn(),
    extendStay: vi.fn(),
    getLookupStati: vi.fn(),
    getLookupTipdoc: vi.fn(),
  },
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const addToast = vi.hoisted(() => vi.fn());
vi.mock('../store/toastStore', () => ({
  useToastStore: (sel: unknown) => (sel as (s: { addToast: typeof addToast }) => unknown)({ addToast }),
}));

const mockNavigate = vi.hoisted(() => vi.fn());
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => mockNavigate,
}));

const STAY = {
  id: 's1',
  reservationId: 'res-1',
  guestId: 'g1',
  roomId: 'r1',
  roomNumber: '204',
  guestDisplayName: 'Bianchi Marco',
  status: 'CHECKED_IN',
  actualCheckInTime: '2026-10-24T14:00:00',
  expectedCheckOutDate: '2026-10-27',
  alloggiatiSent: true,
  alloggiatiSendFailed: false,
  invoiceCreationFailed: false,
  checkoutEmailFailed: false,
  guests: [
    { id: 'sg1', firstName: 'Marco', lastName: 'Bianchi', isPrimaryGuest: true },
    { id: 'sg2', firstName: 'Anna', lastName: 'Bianchi', isPrimaryGuest: false },
  ],
};

const renderDetail = (entry = '/stays/s1') =>
  render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/stays/:id" element={<StayDetail />} />
      </Routes>
    </MemoryRouter>,
  );

describe('StayDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(stayService.getStayById).mockResolvedValue(STAY as never);
  });

  it('shows the room, guest, status, dates and guests of the stay', async () => {
    renderDetail();

    expect(await screen.findByRole('heading', { level: 1, name: /204 · Bianchi Marco/ })).toBeInTheDocument();
    expect(screen.getByText('status_checked_in')).toBeInTheDocument();
    expect(screen.getByText('Bianchi Marco', { selector: 'li span' })).toBeInTheDocument();
    expect(screen.getByText('Bianchi Anna')).toBeInTheDocument();
    expect(screen.getByText('stay_primary_guest')).toBeInTheDocument();
    expect(stayService.getStayById).toHaveBeenCalledWith('s1');
  });

  it('has no accessibility violations', async () => {
    const { container } = renderDetail();
    await screen.findByRole('heading', { level: 1 });
    expect(await axe(container)).toHaveNoViolations();
  });

  it('shows the loading state, then the error state with a retry', async () => {
    vi.mocked(stayService.getStayById).mockRejectedValueOnce(new Error('boom')).mockResolvedValue(STAY as never);
    renderDetail();

    expect(await screen.findByText('error_loading_stays')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'try_again' }));

    expect(await screen.findByRole('heading', { level: 1, name: /204/ })).toBeInTheDocument();
  });

  it('checks the guest out and shows the updated stay', async () => {
    vi.mocked(stayService.checkOut).mockResolvedValue({
      ...STAY, status: 'CHECKED_OUT', actualCheckOutTime: '2026-10-27T10:00:00',
    } as never);
    renderDetail();
    await screen.findByRole('heading', { level: 1 });

    fireEvent.click(screen.getByRole('button', { name: 'action_checkout' }));

    await waitFor(() => expect(screen.getByText('status_checked_out')).toBeInTheDocument());
    expect(stayService.checkOut).toHaveBeenCalledWith('s1');
    expect(addToast).toHaveBeenCalledWith('guest_checked_out_success', 'success');
    expect(screen.queryByRole('button', { name: 'action_checkout' })).not.toBeInTheDocument();
  });

  it('reports a failed check-out and keeps the actions', async () => {
    vi.mocked(stayService.checkOut).mockRejectedValue(new Error('nope'));
    renderDetail();
    await screen.findByRole('heading', { level: 1 });

    fireEvent.click(screen.getByRole('button', { name: 'action_checkout' }));

    await waitFor(() => expect(addToast).toHaveBeenCalledWith('checkout_failed', 'error'));
    expect(screen.getByRole('button', { name: 'action_checkout' })).toBeInTheDocument();
  });

  it('extends the stay from the dialog and shows the new check-out date', async () => {
    const extended = { ...STAY, expectedCheckOutDate: '2026-10-28' };
    vi.mocked(stayService.extendStay).mockResolvedValue(extended as never);
    // The list refresh after the update re-reads the stay, which the server now reports as extended.
    vi.mocked(stayService.getStayById).mockResolvedValueOnce(STAY as never).mockResolvedValue(extended as never);
    renderDetail();
    await screen.findByRole('heading', { level: 1 });
    expect(screen.getByText('10/27/2026')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'action_extend_stay' }));
    await screen.findByText('stay_extension_title');
    fireEvent.click(screen.getByRole('button', { name: 'confirm' }));

    await waitFor(() => expect(screen.getByText('10/28/2026')).toBeInTheDocument());
  });

  it('opens the guest manager only for an in-house stay', async () => {
    vi.mocked(stayService.getLookupStati).mockResolvedValue([]);
    vi.mocked(stayService.getLookupTipdoc).mockResolvedValue([]);
    renderDetail();
    await screen.findByRole('heading', { level: 1 });

    fireEvent.click(screen.getByRole('button', { name: 'stay_manage_guests' }));

    await waitFor(() => expect(stayService.getLookupStati).toHaveBeenCalled());
  });

  it('offers no actions on a closed stay', async () => {
    vi.mocked(stayService.getStayById).mockResolvedValue({ ...STAY, status: 'CHECKED_OUT' } as never);
    renderDetail();
    await screen.findByRole('heading', { level: 1 });

    expect(screen.queryByRole('button', { name: 'action_checkout' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'action_extend_stay' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'action_change_room' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'stay_manage_guests' })).not.toBeInTheDocument();
  });

  it('links to the reservation and the guest profile', async () => {
    renderDetail();
    await screen.findByRole('heading', { level: 1 });

    fireEvent.click(screen.getByRole('button', { name: 'stays_open_reservation' }));
    expect(mockNavigate).toHaveBeenCalledWith('/reservations/res-1');

    fireEvent.click(screen.getByRole('button', { name: 'stay_open_guest' }));
    expect(mockNavigate).toHaveBeenCalledWith('/guests', { state: { guestId: 'g1' } });
  });

  it('hides the reservation link for a walk-in stay', async () => {
    vi.mocked(stayService.getStayById).mockResolvedValue({ ...STAY, reservationId: null } as never);
    renderDetail();
    await screen.findByRole('heading', { level: 1 });

    expect(screen.queryByRole('button', { name: 'stays_open_reservation' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'stay_open_guest' })).toBeInTheDocument();
  });

  it('shows a placeholder when no guests are registered', async () => {
    vi.mocked(stayService.getStayById).mockResolvedValue({ ...STAY, guests: undefined, guestDisplayName: null } as never);
    renderDetail();

    expect(await screen.findByText('stay_no_guests')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: /204 · g1/ })).toBeInTheDocument();
  });

  it('goes back in history, or to the list when opened directly', async () => {
    renderDetail();
    await screen.findByRole('heading', { level: 1 });

    // A direct visit has the router's 'default' location key: no history to go back to.
    fireEvent.click(screen.getByRole('button', { name: 'back' }));

    expect(mockNavigate).toHaveBeenCalledWith('/stays');
  });
});
