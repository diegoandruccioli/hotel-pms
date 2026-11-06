/* eslint-disable react-perf/jsx-no-new-array-as-prop */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { openRowMenu, renderWithQuery as render } from '../test-utils';
import { Stays } from './Stays';
import { stayService } from '../services';
import { useAuthStore } from '../store';

vi.mock('react-i18next', () => {
  const t = (key: string) => key;
  return {
    useTranslation: () => ({ t, i18n: { language: 'en' } }),
    initReactI18next: { type: '3rdParty', init: vi.fn() },
  };
});

vi.mock('../services/stayService', () => ({
  stayService: {
    getAllStays: vi.fn(),
    downloadAlloggiatiJson: vi.fn(),
    downloadAlloggiatiReport: vi.fn(),
    retryInvoiceCreation: vi.fn(),
    retryCheckoutEmail: vi.fn(),
    getStayById: vi.fn(),
    getLookupStati: vi.fn(),
    getLookupTipdoc: vi.fn(),
    extendStay: vi.fn(),
  },
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('../store/authStore', () => ({
  useAuthStore: vi.fn(),
}));

vi.mock('../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

const mockNavigate = vi.hoisted(() => vi.fn());
const mockLocationState = vi.hoisted(() => ({ current: null as Record<string, unknown> | null }));
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => mockNavigate,
  useLocation: () => ({ pathname: '/stays', state: mockLocationState.current, search: '', hash: '', key: 'test' }),
}));

const renderStays = (url = '/stays') =>
  render(<MemoryRouter initialEntries={[url]}><Stays /></MemoryRouter>);

const lastFilters = () => vi.mocked(stayService.getAllStays).mock.calls.at(-1)?.[2];

const EMPTY_PAGE = {
  content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, numberOfElements: 0, first: true, last: true, empty: true,
};

describe('Stays', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockLocationState.current = null;
    vi.mocked(useAuthStore).mockImplementation((selector: unknown) =>
      (selector as (s: { user: null }) => unknown)({ user: null })
    );
  });

  it('should show loading spinner initially', () => {
    vi.mocked(stayService.getAllStays).mockReturnValue(new Promise(() => {}));
    renderStays();
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should render stays on success', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [{ id: 's1', roomId: 'room-1234-abcd', guestId: 'guest-5678-efgh',
        status: 'CHECKED_IN', actualCheckInTime: '2026-03-15T14:00:00' }],
      totalElements: 1, totalPages: 1, number: 0, size: 20,
      numberOfElements: 1, first: true, last: true, empty: false,
    } as never);

    renderStays();

    await waitFor(() => {
      expect(screen.getByText('room-123…')).toBeInTheDocument();
    });
  });

  it('should show empty state when no stays', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [], totalElements: 0, totalPages: 1, number: 0, size: 20,
      numberOfElements: 0, first: true, last: true, empty: true,
    } as never);
    renderStays();

    await waitFor(() => {
      expect(screen.getByText('no_active_stays')).toBeInTheDocument();
    });
  });

  it('should show FAILED badge for a stay with a failed Alloggiati submission', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [{ id: 's1', roomId: 'room-1234-abcd', guestId: 'guest-5678-efgh',
        status: 'CHECKED_IN', actualCheckInTime: '2026-03-15T14:00:00',
        alloggiatiSent: false, alloggiatiSendFailed: true, alloggiatiFailureReason: 'PS portal down' }],
      totalElements: 1, totalPages: 1, number: 0, size: 20,
      numberOfElements: 1, first: true, last: true, empty: false,
    } as never);

    renderStays();

    await waitFor(() => {
      expect(screen.getByText('alloggiati_failed')).toBeInTheDocument();
    });
  });

  it('should show error on failure', async () => {
    vi.mocked(stayService.getAllStays).mockRejectedValueOnce(new Error('Network error'));
    renderStays();

    await waitFor(() => {
      expect(screen.getByText('error_loading_stays')).toBeInTheDocument();
    });
  });

  it('should show invoice-failed badge and retry, clearing the flag on success', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [{ id: 's1', roomId: 'room-1234-abcd', guestId: 'guest-5678-efgh',
        status: 'CHECKED_IN', actualCheckInTime: '2026-03-15T14:00:00',
        invoiceCreationFailed: true, invoiceCreationFailureReason: 'BILLING_SERVICE_UNAVAILABLE' }],
      totalElements: 1, totalPages: 1, number: 0, size: 20,
      numberOfElements: 1, first: true, last: true, empty: false,
    } as never);
    vi.mocked(stayService.retryInvoiceCreation).mockResolvedValueOnce({
      id: 's1', roomId: 'room-1234-abcd', guestId: 'guest-5678-efgh',
      status: 'CHECKED_IN', invoiceCreationFailed: false, invoiceCreationFailureReason: null,
    } as never);

    renderStays();
    await waitFor(() => expect(screen.getByText('invoice_creation_failed')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'retry_invoice_creation' }));

    await waitFor(() => {
      expect(stayService.retryInvoiceCreation).toHaveBeenCalledWith('s1');
      expect(screen.queryByText('invoice_creation_failed')).not.toBeInTheDocument();
    });
  });

  it('should show checkout-email-failed badge and retry on failure keeps the badge', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [{ id: 's1', roomId: 'room-1234-abcd', guestId: 'guest-5678-efgh',
        status: 'CHECKED_OUT', actualCheckInTime: '2026-03-15T14:00:00',
        checkoutEmailFailed: true, checkoutEmailFailureReason: 'NOTIFICATION_SERVICE_UNAVAILABLE' }],
      totalElements: 1, totalPages: 1, number: 0, size: 20,
      numberOfElements: 1, first: true, last: true, empty: false,
    } as never);
    vi.mocked(stayService.retryCheckoutEmail).mockRejectedValueOnce(new Error('still down'));

    renderStays();
    await waitFor(() => expect(screen.getByText('checkout_email_failed')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'retry_checkout_email' }));

    await waitFor(() => {
      expect(stayService.retryCheckoutEmail).toHaveBeenCalledWith('s1');
      expect(screen.getByText('checkout_email_failed')).toBeInTheDocument();
    });
  });

  it('should render page title', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [], totalElements: 0, totalPages: 1, number: 0, size: 20,
      numberOfElements: 0, first: true, last: true, empty: true,
    } as never);
    renderStays();

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: 'nav_stays' })).toBeInTheDocument();
    });
  });

  it('should filter stays by room number on search input', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValue({
      content: [
        { id: 's1', roomId: 'r1', roomNumber: '101', guestId: 'g1', guestDisplayName: 'John Doe', status: 'CHECKED_IN' },
        { id: 's2', roomId: 'r2', roomNumber: '202', guestId: 'g2', guestDisplayName: 'Jane Smith', status: 'CHECKED_IN' },
      ],
      totalElements: 2, totalPages: 1, number: 0, size: 20, numberOfElements: 2, first: true, last: true, empty: false,
    } as never);
    renderStays();
    await waitFor(() => expect(screen.getByText('101')).toBeInTheDocument());

    const input = screen.getByRole('searchbox');
    fireEvent.change(input, { target: { value: '202' } });

    await waitFor(() => {
      expect(screen.queryByText('101')).not.toBeInTheDocument();
      expect(screen.getByText('202')).toBeInTheDocument();
    }, { timeout: 500 });
  });

  it('should filter stays by status chip', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValue({
      content: [
        { id: 's1', roomId: 'r1', roomNumber: '101', guestId: 'g1', guestDisplayName: 'John Doe', status: 'CHECKED_IN' },
        { id: 's2', roomId: 'r2', roomNumber: '202', guestId: 'g2', guestDisplayName: 'Jane Smith', status: 'EXPECTED' },
      ],
      totalElements: 2, totalPages: 1, number: 0, size: 20, numberOfElements: 2, first: true, last: true, empty: false,
    } as never);
    renderStays();
    await waitFor(() => expect(screen.getByText('101')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'status_expected' }));

    // The status is a server filter now: it goes into the request (and resets to the first page).
    await waitFor(() => expect(lastFilters()).toMatchObject({ status: 'EXPECTED' }));
    expect(vi.mocked(stayService.getAllStays).mock.calls.at(-1)?.[0]).toBe(0);
  });

  it('should have no accessibility violations', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [], totalElements: 0, totalPages: 1, number: 0, size: 20,
      numberOfElements: 0, first: true, last: true, empty: true,
    } as never);
    const { container } = renderStays();
    await waitFor(() => expect(screen.getByText('no_active_stays')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });

  it('should not render JSON export button for RECEPTIONIST', async () => {
    vi.mocked(useAuthStore).mockImplementation((selector: unknown) =>
      (selector as (s: { user: { role: string } }) => unknown)({ user: { role: 'RECEPTIONIST' } })
    );
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [], totalElements: 0, totalPages: 1, number: 0, size: 20,
      numberOfElements: 0, first: true, last: true, empty: true,
    } as never);
    renderStays();
    await waitFor(() => expect(screen.getByText('no_active_stays')).toBeInTheDocument());
    expect(screen.queryByText('download_json_export')).not.toBeInTheDocument();
  });

  it('should render JSON export button for ADMIN', async () => {
    vi.mocked(useAuthStore).mockImplementation((selector: unknown) =>
      (selector as (s: { user: { role: string } }) => unknown)({ user: { role: 'ADMIN' } })
    );
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [], totalElements: 0, totalPages: 1, number: 0, size: 20,
      numberOfElements: 0, first: true, last: true, empty: true,
    } as never);
    renderStays();
    await waitFor(() => expect(screen.getByText('no_active_stays')).toBeInTheDocument());
    expect(screen.getByText('download_json_export')).toBeInTheDocument();
  });

  it('should navigate to guests page when guest name is clicked', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValue({
      content: [
        { id: 's1', roomId: 'r1', roomNumber: '101', guestId: 'guest-uuid-001', guestDisplayName: 'John Doe', status: 'CHECKED_IN' },
      ],
      totalElements: 1, totalPages: 1, number: 0, size: 20, numberOfElements: 1, first: true, last: true, empty: false,
    } as never);
    renderStays();
    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'John Doe' }));

    expect(mockNavigate).toHaveBeenCalledWith('/guests', { state: { guestId: 'guest-uuid-001' } });
  });

  it('should render JSON export button for OWNER', async () => {
    vi.mocked(useAuthStore).mockImplementation((selector: unknown) =>
      (selector as (s: { user: { role: string } }) => unknown)({ user: { role: 'OWNER' } })
    );
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [], totalElements: 0, totalPages: 1, number: 0, size: 20,
      numberOfElements: 0, first: true, last: true, empty: true,
    } as never);
    renderStays();
    await waitFor(() => expect(screen.getByText('no_active_stays')).toBeInTheDocument());
    expect(screen.getByText('download_json_export')).toBeInTheDocument();
  });

  it('renders the expected check-out date column', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [
        { id: 's1', roomId: 'r1', roomNumber: '101', guestId: 'g1', guestDisplayName: 'John Doe',
          status: 'CHECKED_IN', expectedCheckOutDate: '2026-07-01' },
      ],
      totalElements: 1, totalPages: 1, number: 0, size: 20, numberOfElements: 1, first: true, last: true, empty: false,
    } as never);
    renderStays();
    await waitFor(() => expect(screen.getByText('7/1/2026')).toBeInTheDocument());
  });

  it('extends a CHECKED_IN stay via the extension dialog', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [
        { id: 's1', roomId: 'r1', roomNumber: '101', guestId: 'g1', guestDisplayName: 'John Doe',
          status: 'CHECKED_IN', expectedCheckOutDate: '2026-07-01' },
      ],
      totalElements: 1, totalPages: 1, number: 0, size: 20, numberOfElements: 1, first: true, last: true, empty: false,
    } as never);
    vi.mocked(stayService.extendStay).mockResolvedValueOnce({
      id: 's1', roomId: 'r1', roomNumber: '101', guestId: 'g1',
      status: 'CHECKED_IN', expectedCheckOutDate: '2026-07-03',
    } as never);

    renderStays();
    await waitFor(() => expect(screen.getByText('7/1/2026')).toBeInTheDocument());

    openRowMenu();
    fireEvent.click(screen.getByRole('menuitem', { name: 'action_extend_stay' }));
    await waitFor(() => expect(screen.getByText('stay_extension_title')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'confirm' }));

    await waitFor(() => {
      expect(stayService.extendStay).toHaveBeenCalledWith('s1', '2026-07-02', undefined);
    });
  });

  it('sorts by expected check-out date when the sort field is changed', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValue({
      content: [
        { id: 's1', roomId: 'r1', roomNumber: '101', guestId: 'g1', guestDisplayName: 'A',
          status: 'CHECKED_IN', expectedCheckOutDate: '2026-07-05' },
        { id: 's2', roomId: 'r2', roomNumber: '202', guestId: 'g2', guestDisplayName: 'B',
          status: 'CHECKED_IN', expectedCheckOutDate: '2026-07-01' },
      ],
      totalElements: 2, totalPages: 1, number: 0, size: 20, numberOfElements: 2, first: true, last: true, empty: false,
    } as never);
    renderStays();
    await waitFor(() => expect(screen.getByText('101')).toBeInTheDocument());

    // Column starts inactive — the first click selects it ascending, and the server does the ordering.
    const expectedCheckoutHeader = screen.getByRole('columnheader', { name: /expected_checkout_col/i });
    fireEvent.click(within(expectedCheckoutHeader).getByRole('button'));

    await waitFor(() => expect(lastFilters()).toMatchObject({ sortField: 'expectedCheckOutDate', sortDir: 'asc' }));
  });

  it('applies statusFilter, sortField and sortDir from navigation state on initial load', async () => {
    mockLocationState.current = { statusFilter: 'CHECKED_IN', sortField: 'expectedCheckOutDate', sortDir: 'asc' };
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [
        { id: 's1', roomId: 'r1', roomNumber: '101', guestId: 'g1', guestDisplayName: 'A',
          status: 'CHECKED_IN', expectedCheckOutDate: '2026-07-05' },
        { id: 's2', roomId: 'r2', roomNumber: '202', guestId: 'g2', guestDisplayName: 'B',
          status: 'EXPECTED', expectedCheckOutDate: '2026-07-01' },
      ],
      totalElements: 2, totalPages: 1, number: 0, size: 20, numberOfElements: 2, first: true, last: true, empty: false,
    } as never);
    renderStays();

    await waitFor(() => expect(screen.getByText('101')).toBeInTheDocument());
    expect(lastFilters()).toMatchObject({ status: 'CHECKED_IN', sortField: 'expectedCheckOutDate', sortDir: 'asc' });
    expect(lastFilters()?.departingOn).toBeUndefined();
  });

  it("turns the dashboard departingToday flag into today's date filter", async () => {
    mockLocationState.current = { statusFilter: 'CHECKED_IN', departingToday: true };
    vi.mocked(stayService.getAllStays).mockResolvedValue(EMPTY_PAGE as never);
    renderStays();

    await waitFor(() => expect(lastFilters()?.departingOn).toMatch(/^\d{4}-\d{2}-\d{2}$/));
    expect(screen.getByRole('button', { name: 'stays_filter_departing_today' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('reads status, sort, departure day and Alloggiati failure from the URL', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValue(EMPTY_PAGE as never);
    renderStays('/stays?status=CHECKED_IN&sort=expectedCheckOutDate&dir=asc&departingOn=2026-10-27&alloggiatiFailed=true');

    await waitFor(() => expect(lastFilters()).toEqual({
      status: 'CHECKED_IN', sortField: 'expectedCheckOutDate', sortDir: 'asc',
      departingOn: '2026-10-27', alloggiatiFailed: true,
    }));
    expect(screen.getByRole('button', { name: 'stays_filter_alloggiati_failed' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('ignores a malformed departure day and unknown status/sort values in the URL', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValue(EMPTY_PAGE as never);
    renderStays('/stays?status=BOGUS&sort=nope&departingOn=tomorrow');

    await waitFor(() => expect(lastFilters()).toMatchObject({ sortField: 'actualCheckInTime', sortDir: 'desc' }));
    expect(lastFilters()?.status).toBeUndefined();
    expect(lastFilters()?.departingOn).toBeUndefined();
  });

  it('toggles the departing-today and Alloggiati-failed chips as server filters', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValue(EMPTY_PAGE as never);
    renderStays();
    await waitFor(() => expect(stayService.getAllStays).toHaveBeenCalled());

    fireEvent.click(screen.getByRole('button', { name: 'stays_filter_alloggiati_failed' }));
    await waitFor(() => expect(lastFilters()).toMatchObject({ alloggiatiFailed: true }));

    fireEvent.click(screen.getByRole('button', { name: 'stays_filter_departing_today' }));
    await waitFor(() => expect(lastFilters()?.departingOn).toBeDefined());

    fireEvent.click(screen.getByRole('button', { name: 'stays_filter_departing_today' }));
    await waitFor(() => expect(lastFilters()?.departingOn).toBeUndefined());
    expect(lastFilters()).toMatchObject({ alloggiatiFailed: true });
  });

  const oneStay = (status: string, reservationId: string | null = 'res-1') => ({
    content: [
      { id: 's1', reservationId, roomId: 'r1', roomNumber: '101', guestId: 'g1',
        guestDisplayName: 'John Doe', status },
    ],
    totalElements: 1, totalPages: 1, number: 0, size: 20, numberOfElements: 1, first: true, last: true, empty: false,
  });

  it('opens the stay detail and the reservation from the row menu of a closed stay', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValue(oneStay('CHECKED_OUT') as never);
    renderStays();
    await waitFor(() => expect(screen.getByText('101')).toBeInTheDocument());

    openRowMenu();
    fireEvent.click(screen.getByRole('menuitem', { name: 'stays_open_detail' }));
    expect(mockNavigate).toHaveBeenCalledWith('/stays/s1');

    openRowMenu();
    fireEvent.click(screen.getByRole('menuitem', { name: 'stays_open_reservation' }));
    expect(mockNavigate).toHaveBeenCalledWith('/reservations/res-1');
  });

  it('keeps only the detail entry, inline, for a walk-in stay without a reservation', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValue(oneStay('CHECKED_OUT', null) as never);
    renderStays();
    await waitFor(() => expect(screen.getByText('101')).toBeInTheDocument());

    expect(screen.queryByRole('button', { name: 'stays_open_reservation' })).not.toBeInTheDocument();
    // A single action stays inline instead of becoming a one-item menu.
    fireEvent.click(screen.getByRole('button', { name: 'stays_open_detail' }));

    expect(mockNavigate).toHaveBeenCalledWith('/stays/s1');
  });

  it('opens the reservation from the row menu of an in-house stay', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValue(oneStay('CHECKED_IN') as never);
    renderStays();
    await waitFor(() => expect(screen.getByText('101')).toBeInTheDocument());

    openRowMenu();
    fireEvent.click(screen.getByRole('menuitem', { name: 'stays_open_reservation' }));

    expect(mockNavigate).toHaveBeenCalledWith('/reservations/res-1');
  });

  it("starts a new check-in from today's arrivals", async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValue(EMPTY_PAGE as never);
    renderStays();
    await waitFor(() => expect(stayService.getAllStays).toHaveBeenCalled());

    fireEvent.click(screen.getByRole('button', { name: /new_checkin/ }));

    expect(mockNavigate).toHaveBeenCalledWith('/reservations', {
      state: { preset: 'arrivalsToday', sortField: 'checkInDate', sortDir: 'asc' },
    });
  });

  describe('toolbar and range', () => {
    const TWO_STAYS = {
      content: [
        { id: 's1', roomId: 'r1', roomNumber: '101', guestId: 'g1', guestDisplayName: 'John Doe', status: 'CHECKED_IN' },
        { id: 's2', roomId: 'r2', roomNumber: '202', guestId: 'g2', guestDisplayName: 'Jane Smith', status: 'EXPECTED' },
      ],
      totalElements: 2, totalPages: 1, number: 0, size: 20, numberOfElements: 2, first: true, last: true, empty: false,
    };

    it('groups the status chips under the filter label', async () => {
      vi.mocked(stayService.getAllStays).mockResolvedValue(TWO_STAYS as never);
      renderStays();
      await waitFor(() => expect(screen.getByText('101')).toBeInTheDocument());

      const group = screen.getByRole('group', { name: 'filter_status' });
      expect(within(group).getAllByRole('button')).toHaveLength(6); // 4 statuses + departing today + Alloggiati failed
    });

    it('shows the server-wide range, and hides it while the page-only search narrows the list', async () => {
      vi.mocked(stayService.getAllStays).mockResolvedValue(TWO_STAYS as never);
      renderStays();
      await waitFor(() => expect(screen.getByText('list_range_summary')).toBeInTheDocument());

      fireEvent.change(screen.getByRole('searchbox'), { target: { value: '202' } });

      await waitFor(() => expect(screen.queryByText('list_range_summary')).not.toBeInTheDocument());
    });

    it('has no range summary for an empty list', async () => {
      vi.mocked(stayService.getAllStays).mockResolvedValue({
        content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, numberOfElements: 0, first: true, last: true, empty: true,
      } as never);
      renderStays();
      await waitFor(() => expect(screen.getByText('no_active_stays')).toBeInTheDocument());
      expect(screen.queryByText('list_range_summary')).not.toBeInTheDocument();
    });
  });
});
