import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent, within } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { renderWithQuery as render } from '../test-utils';
import { Guests } from './Guests';

const mockUseSearchParams = vi.hoisted(() => vi.fn(() => [new URLSearchParams()] as [URLSearchParams]));
const mockNavigate = vi.hoisted(() => vi.fn());
vi.mock('react-router-dom', () => ({
  useSearchParams: mockUseSearchParams,
  useNavigate: () => mockNavigate,
  useLocation: () => ({ pathname: '/guests' }),
}));
import { guestService, stayService, billingService, inventoryService } from '../services';
import { useAuthStore } from '../store';
import { useToastStore } from '../store';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/guestService', () => ({
  guestService: {
    searchGuestsPaged: vi.fn(),
    deleteGuest: vi.fn(),
    downloadGuestDataExport: vi.fn(),
    exportGuestsCsv: vi.fn(),
  },
}));

vi.mock('../services/stayService', () => ({ stayService: { getGuestStayHistory: vi.fn() } }));
vi.mock('../services/billingService', () => ({ billingService: { getGuestInvoiceHistory: vi.fn() } }));
vi.mock('../services/inventoryService', () => ({ inventoryService: { getAllRooms: vi.fn() } }));

vi.mock('../store/authStore', () => ({
  useAuthStore: vi.fn(),
}));

vi.mock('../store/toastStore', () => ({
  useToastStore: vi.fn(),
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('./GuestFormModal', () => ({
  GuestFormModal: ({ onSaved }: { onSaved: () => void }) => (
    <button type="button" onClick={onSaved}>trigger-saved</button>
  ),
}));

const GUEST = {
  id: 'guest-1',
  firstName: 'John',
  lastName: 'Doe',
  email: 'john@test.com',
  phone: '123',
  city: 'Rome',
  country: 'IT',
};

const JANE = {
  id: 'g2',
  firstName: 'Jane',
  lastName: 'Smith',
  email: 'jane@test.com',
  phone: '',
  city: '',
  country: '',
};

const page = (content: unknown[], totalPages = 1) => ({ content, totalPages, totalElements: content.length });

const mockAddToast = vi.fn();

const mockAuthAdmin = (selector: unknown) =>
  (selector as (s: { user: { role: string } }) => unknown)({ user: { role: 'ADMIN' } });

const mockAuthReceptionist = (selector: unknown) =>
  (selector as (s: { user: { role: string } }) => unknown)({ user: { role: 'RECEPTIONIST' } });

const mockAuthNull = (selector: unknown) =>
  (selector as (s: { user: null }) => unknown)({ user: null });

describe('Guests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAuthStore).mockImplementation(mockAuthNull);
    vi.mocked(useToastStore).mockReturnValue(mockAddToast);
    vi.mocked(stayService.getGuestStayHistory).mockResolvedValue([]);
    vi.mocked(billingService.getGuestInvoiceHistory).mockResolvedValue([]);
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue({ content: [] } as never);
  });

  it('should show loading spinner initially', () => {
    vi.mocked(guestService.searchGuestsPaged).mockReturnValue(new Promise(() => {}));
    render(<Guests />);
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should render guest list on success', async () => {
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);

    render(<Guests />);

    await waitFor(() => {
      expect(screen.getByText('John Doe')).toBeInTheDocument();
    });
    expect(guestService.searchGuestsPaged).toHaveBeenCalledWith('', 0, 20, 'lastName,asc');
  });

  it('should show empty state message when no guests', async () => {
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([]) as never);
    render(<Guests />);

    await waitFor(() => {
      expect(screen.getByText('no_guests_found')).toBeInTheDocument();
    });
  });

  it('should show error message on failure', async () => {
    vi.mocked(guestService.searchGuestsPaged).mockRejectedValueOnce(new Error('Network error'));
    render(<Guests />);

    await waitFor(() => {
      expect(screen.getByText('error_loading_guests')).toBeInTheDocument();
    });
  });

  it('should render page title', async () => {
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([]) as never);
    render(<Guests />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: 'nav_guests' })).toBeInTheDocument();
    });
  });

  // T-FE-01: React JSX output encoding — XSS payloads in API data must be escaped as text
  it('should escape XSS payload in guest fields (T-FE-01)', async () => {
    const xssFirst = '<img src=x onerror=alert(1)>';
    const xssEmail = '"><svg onload=alert(2)>';
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([
      { id: '1', firstName: xssFirst, lastName: 'XSS', email: xssEmail, phone: '', city: '', country: '' },
    ]) as never);

    const { container } = render(<Guests />);

    await waitFor(() => {
      // No injected elements: React must not have interpreted the payload as HTML
      expect(container.querySelector('img[onerror]')).toBeNull();
      expect(container.querySelector('svg[onload]')).toBeNull();
      // Payload is present as literal text content (HTML-escaped by React JSX)
      const cells = container.querySelectorAll('td');
      const nameCellText = cells[0]?.textContent ?? '';
      expect(nameCellText).toContain(xssFirst);
    });
  });

  it('should not show delete button for RECEPTIONIST', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthReceptionist);
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: /delete John Doe/ })).not.toBeInTheDocument();
  });

  it('should show delete button for ADMIN', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /delete John Doe/ })).toBeInTheDocument();
  });

  it('should not show the export CSV button for RECEPTIONIST', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthReceptionist);
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    expect(screen.queryByText('export_csv')).not.toBeInTheDocument();
  });

  it('should export the current search as CSV when ADMIN clicks export', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    fireEvent.click(screen.getByText('export_csv'));

    expect(guestService.exportGuestsCsv).toHaveBeenCalledWith('');
  });

  it('should open confirmation dialog when delete button is clicked', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /delete John Doe/ }));

    expect(screen.getByText('delete_guest_confirm')).toBeInTheDocument();
  });

  it('should call deleteGuest and reload the current page on confirm', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(guestService.searchGuestsPaged)
      .mockResolvedValueOnce(page([GUEST]) as never)
      .mockResolvedValueOnce(page([]) as never);
    vi.mocked(guestService.deleteGuest).mockResolvedValueOnce(undefined);
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /delete John Doe/ }));
    fireEvent.click(screen.getAllByRole('button', { name: /^delete$/ })[0]);

    await waitFor(() => {
      expect(guestService.deleteGuest).toHaveBeenCalledWith('guest-1');
      expect(mockAddToast).toHaveBeenCalledWith('guest_deleted_success', 'success');
      expect(screen.queryByText('John Doe')).not.toBeInTheDocument();
    });
  });

  it('should close the delete confirmation dialog on cancel', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /delete John Doe/ }));
    expect(screen.getByText('delete_guest_confirm')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'cancel' }));

    expect(screen.queryByText('delete_guest_confirm')).not.toBeInTheDocument();
    expect(guestService.deleteGuest).not.toHaveBeenCalled();
  });

  it('should show a generic failure toast on a non-GDPR delete error', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
    vi.mocked(guestService.deleteGuest).mockRejectedValueOnce({ response: { status: 500 } });
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /delete John Doe/ }));
    fireEvent.click(screen.getAllByRole('button', { name: /^delete$/ })[0]);

    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith('delete_guest_failed', 'error');
    });
  });

  it('should reload the guest list when the form modal reports a save', async () => {
    vi.mocked(guestService.searchGuestsPaged)
      .mockResolvedValueOnce(page([GUEST]) as never)
      .mockResolvedValueOnce(page([GUEST, JANE]) as never);
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'add_guest' }));
    fireEvent.click(screen.getByText('trigger-saved'));

    await waitFor(() => {
      expect(guestService.searchGuestsPaged).toHaveBeenCalledTimes(2);
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
    });
  });

  it('should show GDPR hold toast on 451 response', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
    vi.mocked(guestService.deleteGuest).mockRejectedValueOnce({ response: { status: 451 } });
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /delete John Doe/ }));
    fireEvent.click(screen.getAllByRole('button', { name: /^delete$/ })[0]);

    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith('delete_guest_gdpr_hold', 'error');
    });
  });

  it('should search guests server-side on search input (not just filter the loaded page)', async () => {
    vi.mocked(guestService.searchGuestsPaged)
      .mockResolvedValueOnce(page([GUEST]) as never)
      .mockResolvedValueOnce(page([JANE]) as never);
    render(<Guests />);
    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());

    const input = screen.getByRole('searchbox');
    fireEvent.change(input, { target: { value: 'Jane' } });

    await waitFor(() => {
      expect(guestService.searchGuestsPaged).toHaveBeenCalledWith('Jane', 0, 20, 'lastName,asc');
      expect(screen.queryByText('John Doe')).not.toBeInTheDocument();
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
    }, { timeout: 500 });
  });

  it('should pre-populate search from URL ?search param and query the server with it', async () => {
    mockUseSearchParams.mockReturnValueOnce([new URLSearchParams('search=Jane')] as [URLSearchParams]);
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([JANE]) as never);
    render(<Guests />);

    await waitFor(() => {
      expect(guestService.searchGuestsPaged).toHaveBeenCalledWith('Jane', 0, 20, 'lastName,asc');
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
    });
  });

  it('should show pagination controls and request the next page on click', async () => {
    vi.mocked(guestService.searchGuestsPaged)
      .mockResolvedValueOnce(page([GUEST], 2) as never)
      .mockResolvedValueOnce(page([JANE], 2) as never);
    render(<Guests />);
    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'next_page' }));

    await waitFor(() => {
      expect(guestService.searchGuestsPaged).toHaveBeenCalledWith('', 1, 20, 'lastName,asc');
      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
    });
  });

  it('should not show export button for RECEPTIONIST', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthReceptionist);
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: /export_guest_data John Doe/ })).not.toBeInTheDocument();
  });

  it('should show export button for ADMIN and open a confirmation dialog', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /export_guest_data John Doe/ }));

    expect(screen.getByText('export_guest_data_confirm_body')).toBeInTheDocument();
    expect(guestService.downloadGuestDataExport).not.toHaveBeenCalled();
  });

  it('should trigger the export download on confirm and close the dialog', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
    vi.mocked(guestService.downloadGuestDataExport).mockResolvedValueOnce(undefined);
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /export_guest_data John Doe/ }));
    fireEvent.click(screen.getByRole('button', { name: 'export_guest_data_confirm_action' }));

    await waitFor(() => {
      expect(guestService.downloadGuestDataExport).toHaveBeenCalledWith('guest-1');
      expect(screen.queryByText('export_guest_data_confirm_body')).not.toBeInTheDocument();
    });
  });

  it('should show an error toast when the export fails', async () => {
    vi.mocked(useAuthStore).mockImplementation(mockAuthAdmin);
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
    vi.mocked(guestService.downloadGuestDataExport).mockRejectedValueOnce(new Error('network'));
    render(<Guests />);

    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /export_guest_data John Doe/ }));
    fireEvent.click(screen.getByRole('button', { name: 'export_guest_data_confirm_action' }));

    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith('export_guest_data_failed', 'error');
      // Dialog stays open on failure so the operator can retry.
      expect(screen.getByText('export_guest_data_confirm_body')).toBeInTheDocument();
    });
  });

  it('should have no accessibility violations', async () => {
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([]) as never);
    const { container } = render(<Guests />);
    await waitFor(() => expect(screen.getByText('no_guests_found')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });

  describe('list presentation', () => {
    it('shows the avatar initial and the email under the name', async () => {
      vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
      render(<Guests />);
      await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());

      expect(screen.getByText('J')).toBeInTheDocument();
      const nameCell = screen.getByText('John Doe').closest('div') as HTMLElement;
      expect(within(nameCell).getByText('john@test.com')).toBeInTheDocument();
    });

    it('keeps phone and city columns and drops the separate email column', async () => {
      vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
      render(<Guests />);
      await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());

      expect(screen.getByRole('columnheader', { name: 'phone' })).toBeInTheDocument();
      expect(screen.getByRole('columnheader', { name: 'city' })).toBeInTheDocument();
      expect(screen.queryByRole('columnheader', { name: 'email' })).not.toBeInTheDocument();
    });

    it('shows the result range, also with a single page', async () => {
      vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST, JANE]) as never);
      render(<Guests />);
      await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());
      expect(screen.getByText('list_range_summary')).toBeInTheDocument();
    });

    it('puts the search in the toolbar and still searches as you type', async () => {
      vi.mocked(guestService.searchGuestsPaged).mockResolvedValue(page([GUEST]) as never);
      render(<Guests />);
      await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());

      fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'doe' } });

      await waitFor(() => expect(guestService.searchGuestsPaged).toHaveBeenCalledWith('doe', 0, 20, 'lastName,asc'));
    });
  });

  it('opens the detail sheet from the guest name', async () => {
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
    render(<Guests />);
    fireEvent.click(await screen.findByRole('button', { name: /John Doe/ }));
    expect(screen.getByRole('dialog', { name: 'detail_title' })).toBeInTheDocument();
    await waitFor(() => expect(stayService.getGuestStayHistory).toHaveBeenCalledWith('guest-1'));
  });

  it('closes the detail sheet and opens the form when Edit is pressed in the sheet', async () => {
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
    render(<Guests />);
    fireEvent.click(await screen.findByRole('button', { name: /John Doe/ }));
    const sheet = screen.getByRole('dialog', { name: 'detail_title' });
    fireEvent.click(within(sheet).getByRole('button', { name: 'common:edit' }));
    expect(screen.queryByRole('dialog', { name: 'detail_title' })).not.toBeInTheDocument();
    expect(screen.getByText('trigger-saved')).toBeInTheDocument();
  });

  it('starts a reservation for the guest from the sheet', async () => {
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
    render(<Guests />);
    fireEvent.click(await screen.findByRole('button', { name: /John Doe/ }));
    const sheet = screen.getByRole('dialog', { name: 'detail_title' });
    fireEvent.click(within(sheet).getByRole('button', { name: 'common:new_reservation' }));
    expect(mockNavigate).toHaveBeenCalledWith('/reservations/new', { state: { guest: GUEST } });
  });

  it('closes the detail sheet with its close button', async () => {
    vi.mocked(guestService.searchGuestsPaged).mockResolvedValueOnce(page([GUEST]) as never);
    render(<Guests />);
    fireEvent.click(await screen.findByRole('button', { name: /John Doe/ }));
    fireEvent.click(screen.getByRole('button', { name: 'close' }));
    expect(screen.queryByRole('dialog', { name: 'detail_title' })).not.toBeInTheDocument();
  });
});
