import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent, within } from '@testing-library/react';
import { useMemo } from 'react';
import { axe } from 'vitest-axe';
import { MemoryRouter, useLocation } from 'react-router-dom';

const mockNavigate = vi.hoisted(() => vi.fn());
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});
import { Quotations } from './Quotations';
import { quotationService } from '../services';
import { useToastStore } from '../store';
import { guestService } from '../services/guestService';
import { openRowMenu, renderWithQuery } from '../test-utils';

// One stable `t` for every render: a new function each time would re-create the load callback and refetch.
const i18nMock = vi.hoisted(() => {
  const t = (key: string, opts?: Record<string, unknown>) => {
    if (opts && typeof opts.amount !== 'undefined') return `${key}:${opts.amount}`;
    if (opts && typeof opts.name !== 'undefined') return `${key}:${opts.name}`;
    return key;
  };
  return { t };
});
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: i18nMock.t, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/quotationService', () => ({
  quotationService: {
    searchQuotations: vi.fn(),
    sendQuotation: vi.fn(),
    convertToReservation: vi.fn(),
    declineQuotation: vi.fn(),
    deleteQuotation: vi.fn(),
    downloadPdf: vi.fn(),
  },
}));

vi.mock('../services/guestService', () => ({
  guestService: { getGuestById: vi.fn() },
}));

vi.mock('../store/toastStore', () => ({
  useToastStore: vi.fn(),
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const mockAddToast = vi.fn();

const DRAFT_QUOTATION = {
  id: 'q1',
  guestId: 'g1',
  guestFullName: 'Mario Rossi',
  prospectEmail: null,
  checkInDate: '2026-09-01',
  checkOutDate: '2026-09-03',
  expectedGuests: 2,
  status: 'DRAFT',
  validUntil: '2026-08-20',
  totalPrice: 200,
  options: [
    { id: 'opt1', label: 'Opzione 1', position: 0, totalPrice: 200, lineItems: [] },
  ],
  acceptedOptionId: null,
  reservationId: null,
  sendFailed: false,
  sendFailureReason: null,
  createdAt: '2026-08-01T00:00:00',
  updatedAt: '2026-08-01T00:00:00',
};

const MULTI_OPTION_QUOTATION = {
  ...DRAFT_QUOTATION,
  id: 'q2',
  guestFullName: 'Luigi Verdi',
  totalPrice: 150,
  options: [
    { id: 'opt1', label: 'Opzione 1', position: 0, totalPrice: 150, lineItems: [] },
    { id: 'opt2', label: 'Opzione 2', position: 1, totalPrice: 220, lineItems: [] },
  ],
};

const page = (content: unknown[], totalPages = 1) => ({ content, totalPages, totalElements: content.length });

// What the page asks the service for; every list request carries these four fields.
const listQuery = (over: Partial<{ status: string; query: string; page: number }> = {}) => ({
  status: undefined, query: '', page: 0, size: 20, ...over,
});

const LocationProbe = () => <output data-testid="search">{useLocation().search}</output>;

const Harness = ({ entry }: { entry: string }) => {
  const entries = useMemo(() => [entry], [entry]);
  return (
    <MemoryRouter initialEntries={entries}>
      <Quotations />
      <LocationProbe />
    </MemoryRouter>
  );
};

const renderPage = (entry = '/quotations') => renderWithQuery(<Harness entry={entry} />);

const GUEST = { id: 'g1', firstName: 'Mario', lastName: 'Rossi', email: 'mario.rossi@example.com' };

// Sending asks first, and the dialog is enabled only once the recipient's address is known.
const confirmSend = async (label = 'send_dialog_confirm') => {
  const confirm = await screen.findByRole('button', { name: label });
  await waitFor(() => expect(confirm).toBeEnabled());
  fireEvent.click(confirm);
};

describe('Quotations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useToastStore).mockImplementation((sel: unknown) =>
      (sel as (s: { addToast: typeof mockAddToast }) => unknown)({ addToast: mockAddToast }));
    vi.mocked(guestService.getGuestById).mockResolvedValue(GUEST as never);
  });

  it('renders heading', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([]) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('title')).toBeInTheDocument());
  });

  it('renders a quotation row after data loads', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('Mario Rossi')).toBeInTheDocument());
    expect(screen.getAllByText('€200.00')).toHaveLength(2);
  });

  it('renders empty state when no quotations', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([]) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('empty_title')).toBeInTheDocument());
  });

  it('the empty state offers to create the first quotation', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([]) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('empty_title')).toBeInTheDocument());
    const buttons = screen.getAllByRole('button', { name: 'new_quotation' });
    expect(buttons).toHaveLength(2);
    fireEvent.click(buttons[1]);
    expect(mockNavigate).toHaveBeenCalledWith('/quotations/new');
  });

  it('renders error state on load failure', async () => {
    vi.mocked(quotationService.searchQuotations).mockRejectedValue(new Error('fail'));
    renderPage();
    await waitFor(() => expect(screen.getAllByText('error_loading_quotations').length).toBeGreaterThan(0));
  });

  it('new_quotation button navigates to /quotations/new', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([]) as never);
    renderPage();
    await waitFor(() => expect(screen.getAllByText('new_quotation').length).toBeGreaterThan(0));
    fireEvent.click(screen.getAllByText('new_quotation')[0]);
    expect(mockNavigate).toHaveBeenCalledWith('/quotations/new');
  });

  it('action_send calls sendQuotation and shows success toast', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    vi.mocked(quotationService.sendQuotation).mockResolvedValue({ ...DRAFT_QUOTATION, status: 'SENT', sendFailed: false } as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('action_send')).toBeInTheDocument());
    fireEvent.click(screen.getByText('action_send'));
    await confirmSend();
    await waitFor(() => expect(quotationService.sendQuotation).toHaveBeenCalledWith('q1'));
    expect(mockAddToast).toHaveBeenCalledWith('toast_sent', 'success');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('action_send asks first and shows who receives the email; nothing is sent until confirmed', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('action_send')).toBeInTheDocument());
    fireEvent.click(screen.getByText('action_send'));

    expect(await screen.findByText('mario.rossi@example.com')).toBeInTheDocument();
    expect(guestService.getGuestById).toHaveBeenCalledWith('g1');
    expect(quotationService.sendQuotation).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'common:cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(quotationService.sendQuotation).not.toHaveBeenCalled();
  });

  it('a SENT quotation asks to resend, with the resend wording', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([{ ...DRAFT_QUOTATION, status: 'SENT' }]) as never);
    vi.mocked(quotationService.sendQuotation).mockResolvedValue({ ...DRAFT_QUOTATION, status: 'SENT' } as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('action_resend')).toBeInTheDocument());
    fireEvent.click(screen.getByText('action_resend'));
    expect(await screen.findByText('send_dialog_title_resend')).toBeInTheDocument();
    await confirmSend('send_dialog_confirm_resend');
    await waitFor(() => expect(quotationService.sendQuotation).toHaveBeenCalledWith('q1'));
  });

  it('action_send shows error toast when send fails', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    vi.mocked(quotationService.sendQuotation).mockResolvedValue({ ...DRAFT_QUOTATION, sendFailed: true } as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('action_send')).toBeInTheDocument());
    fireEvent.click(screen.getByText('action_send'));
    await confirmSend();
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('toast_send_failed', 'error'));
  });

  it('action_send shows error toast when sendQuotation rejects', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    vi.mocked(quotationService.sendQuotation).mockRejectedValue(new Error('boom'));
    renderPage();
    await waitFor(() => expect(screen.getByText('action_send')).toBeInTheDocument());
    fireEvent.click(screen.getByText('action_send'));
    await confirmSend();
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('toast_send_failed', 'error'));
  });

  it('action_convert on a single-option quotation asks first, then converts and opens the new reservation', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    vi.mocked(quotationService.convertToReservation).mockResolvedValue({ id: 'res1' } as never);
    renderPage();
    await waitFor(() => expect(screen.getAllByRole('button', { name: /row_actions_for/ }).length).toBeGreaterThan(0));
    openRowMenu();
    fireEvent.click(screen.getByRole('menuitem', { name: 'action_convert' }));
    expect(screen.getByText('list_convert_message:Mario Rossi')).toBeInTheDocument();
    expect(quotationService.convertToReservation).not.toHaveBeenCalled();
    fireEvent.click(screen.getByText('list_convert_confirm'));
    await waitFor(() => expect(quotationService.convertToReservation).toHaveBeenCalledWith('q1'));
    expect(mockAddToast).toHaveBeenCalledWith('toast_converted', 'success');
    expect(mockNavigate).toHaveBeenCalledWith('/reservations/res1');
    expect(mockNavigate).not.toHaveBeenCalledWith('/quotations/q1');
  });

  it('offers a link to the reservation only for a converted quotation that recorded it', async () => {
    const converted = { ...DRAFT_QUOTATION, status: 'ACCEPTED', reservationId: 'res9' };
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([converted]) as never);
    renderPage();
    await waitFor(() => expect(screen.getAllByRole('button', { name: /row_actions_for/ }).length).toBeGreaterThan(0));
    openRowMenu();
    fireEvent.click(screen.getByRole('menuitem', { name: 'action_open_reservation' }));
    expect(mockNavigate).toHaveBeenCalledWith('/reservations/res9');
  });

  it('hides the reservation link when the quotation has none', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    renderPage();
    await waitFor(() => expect(screen.getAllByRole('button', { name: /row_actions_for/ }).length).toBeGreaterThan(0));
    openRowMenu();
    expect(screen.queryByRole('menuitem', { name: 'action_open_reservation' })).not.toBeInTheDocument();
  });

  it('action_convert on a single-option quotation shows an error toast when conversion fails', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    vi.mocked(quotationService.convertToReservation).mockRejectedValue(new Error('boom'));
    renderPage();
    await waitFor(() => expect(screen.getAllByRole('button', { name: /row_actions_for/ }).length).toBeGreaterThan(0));
    openRowMenu();
    fireEvent.click(screen.getByRole('menuitem', { name: 'action_convert' }));
    fireEvent.click(screen.getByText('list_convert_confirm'));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('err_convert', 'error'));
  });

  it('action_decline shows confirmation dialog then declines', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    vi.mocked(quotationService.declineQuotation).mockResolvedValue({ ...DRAFT_QUOTATION, status: 'DECLINED' } as never);
    renderPage();
    await waitFor(() => expect(screen.getAllByRole('button', { name: /row_actions_for/ }).length).toBeGreaterThan(0));
    openRowMenu();
    fireEvent.click(screen.getByRole('menuitem', { name: 'action_decline' }));
    expect(screen.getByText('list_decline_message:Mario Rossi')).toBeInTheDocument();
    fireEvent.click(screen.getByText('list_decline_confirm'));
    await waitFor(() => expect(quotationService.declineQuotation).toHaveBeenCalledWith('q1'));
    expect(mockAddToast).toHaveBeenCalledWith('toast_declined', 'success');
  });

  it('action_decline shows an error toast when declineQuotation rejects', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    vi.mocked(quotationService.declineQuotation).mockRejectedValue(new Error('boom'));
    renderPage();
    await waitFor(() => expect(screen.getAllByRole('button', { name: /row_actions_for/ }).length).toBeGreaterThan(0));
    openRowMenu();
    fireEvent.click(screen.getByRole('menuitem', { name: 'action_decline' }));
    fireEvent.click(screen.getByText('list_decline_confirm'));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('err_decline', 'error'));
  });

  it('action_delete shows an error toast when deleteQuotation rejects', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    vi.mocked(quotationService.deleteQuotation).mockRejectedValue(new Error('boom'));
    renderPage();
    await waitFor(() => expect(screen.getAllByRole('button', { name: /row_actions_for/ }).length).toBeGreaterThan(0));
    openRowMenu();
    fireEvent.click(screen.getByRole('menuitem', { name: 'action_delete' }));
    fireEvent.click(screen.getByText('list_delete_confirm'));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('err_delete', 'error'));
  });

  it('shows a single price (not a range) when all options happen to total the same amount', async () => {
    const equalOptionsQuotation = {
      ...MULTI_OPTION_QUOTATION,
      id: 'q3',
      guestFullName: 'Anna Neri',
      options: [
        { id: 'opt1', label: 'Opzione 1', position: 0, totalPrice: 90, lineItems: [] },
        { id: 'opt2', label: 'Opzione 2', position: 1, totalPrice: 90, lineItems: [] },
      ],
    };
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([equalOptionsQuotation]) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('Anna Neri')).toBeInTheDocument());
    expect(screen.getAllByText('€90.00')).toHaveLength(2);
    expect(screen.queryByText('price_range')).not.toBeInTheDocument();
  });

  it('action_download_pdf calls quotationService.downloadPdf', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    renderPage();
    await waitFor(() => expect(screen.getAllByRole('button', { name: /row_actions_for/ }).length).toBeGreaterThan(0));
    openRowMenu();
    fireEvent.click(screen.getByRole('menuitem', { name: 'action_download_pdf' }));
    expect(quotationService.downloadPdf).toHaveBeenCalledWith('q1');
  });

  it('paginates forward and back', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION], 3) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('common:page_x_of_y')).toBeInTheDocument());

    fireEvent.click(screen.getByLabelText('common:next_page'));
    await waitFor(() => expect(quotationService.searchQuotations).toHaveBeenCalledWith(listQuery({ page: 1 })));

    fireEvent.click(screen.getByLabelText('common:prev_page'));
    await waitFor(() => expect(quotationService.searchQuotations).toHaveBeenCalledWith(listQuery()));
  });

  it('cancelling the decline dialog closes it without declining', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    renderPage();
    await waitFor(() => expect(screen.getAllByRole('button', { name: /row_actions_for/ }).length).toBeGreaterThan(0));
    openRowMenu();
    fireEvent.click(screen.getByRole('menuitem', { name: 'action_decline' }));
    expect(screen.getByText('list_decline_message:Mario Rossi')).toBeInTheDocument();
    fireEvent.click(screen.getByText('cancel'));
    expect(screen.queryByText('list_decline_message:Mario Rossi')).not.toBeInTheDocument();
    expect(quotationService.declineQuotation).not.toHaveBeenCalled();
  });

  it('cancelling the delete dialog closes it without deleting', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    renderPage();
    await waitFor(() => expect(screen.getAllByRole('button', { name: /row_actions_for/ }).length).toBeGreaterThan(0));
    openRowMenu();
    fireEvent.click(screen.getByRole('menuitem', { name: 'action_delete' }));
    expect(screen.getByText('list_delete_message:Mario Rossi')).toBeInTheDocument();
    fireEvent.click(screen.getByText('cancel'));
    expect(screen.queryByText('list_delete_message:Mario Rossi')).not.toBeInTheDocument();
    expect(quotationService.deleteQuotation).not.toHaveBeenCalled();
  });

  it('action_delete shows confirmation dialog then deletes', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    vi.mocked(quotationService.deleteQuotation).mockResolvedValue(undefined);
    renderPage();
    await waitFor(() => expect(screen.getAllByRole('button', { name: /row_actions_for/ }).length).toBeGreaterThan(0));
    openRowMenu();
    fireEvent.click(screen.getByRole('menuitem', { name: 'action_delete' }));
    expect(screen.getByText('list_delete_message:Mario Rossi')).toBeInTheDocument();
    fireEvent.click(screen.getByText('list_delete_confirm'));
    await waitFor(() => expect(quotationService.deleteQuotation).toHaveBeenCalledWith('q1'));
  });

  it('reads the page from the URL (one-based) and asks the service for it', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION], 3) as never);
    renderPage('/quotations?page=2');
    await waitFor(() => expect(quotationService.searchQuotations).toHaveBeenCalledWith(listQuery({ page: 1 })));
  });

  it('writes the next page to the URL, and drops it again on the first page', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION], 3) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('common:page_x_of_y')).toBeInTheDocument());
    fireEvent.click(screen.getByLabelText('common:next_page'));
    await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('?page=2'));
    fireEvent.click(screen.getByLabelText('common:prev_page'));
    await waitFor(() => expect(screen.getByTestId('search')).toBeEmptyDOMElement());
  });

  it('moves to the last existing page when the current one is empty', async () => {
    vi.mocked(quotationService.searchQuotations)
      .mockResolvedValueOnce({ content: [], totalPages: 2, totalElements: 20 } as never)
      .mockResolvedValue(page([DRAFT_QUOTATION], 2) as never);
    renderPage('/quotations?page=5');
    await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('?page=2'));
    await waitFor(() => expect(quotationService.searchQuotations).toHaveBeenLastCalledWith(listQuery({ page: 1 })));
    await waitFor(() => expect(screen.getByText('Mario Rossi')).toBeInTheDocument());
  });

  it('starts on "all": the chip is pressed and the service gets no status or query', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    renderPage();
    await waitFor(() => expect(quotationService.searchQuotations).toHaveBeenCalledWith(listQuery()));
    expect(screen.getByRole('button', { name: 'common:filter_all' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'status_expired' })).toHaveAttribute('aria-pressed', 'false');
  });

  it('reads the status filter from the URL', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    renderPage('/quotations?status=SENT&page=2');
    await waitFor(() => expect(quotationService.searchQuotations).toHaveBeenCalledWith(
      listQuery({ status: 'SENT', page: 1 }),
    ));
    expect(screen.getByRole('button', { name: 'status_sent' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('a status chip filters on the server, writes the status to the URL and goes back to the first page', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION], 3) as never);
    renderPage('/quotations?page=2');
    await waitFor(() => expect(screen.getByText('Mario Rossi')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'status_expired' }));

    await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('?status=EXPIRED'));
    expect(screen.getByTestId('search')).not.toHaveTextContent('page=');
    await waitFor(() => expect(quotationService.searchQuotations).toHaveBeenLastCalledWith(
      listQuery({ status: 'EXPIRED' }),
    ));
  });

  it('searches by the debounced text and keeps the text out of the URL', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('Mario Rossi')).toBeInTheDocument());

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: '  rossi ' } });

    await waitFor(() => expect(quotationService.searchQuotations).toHaveBeenLastCalledWith(
      listQuery({ query: '  rossi ' }),
    ));
    // The text holds a guest's name or email: it must never reach the address bar.
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();
  });

  it('going to the first page happens as soon as the search text changes on a later page', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION], 3) as never);
    renderPage('/quotations?page=3');
    await waitFor(() => expect(screen.getByText('Mario Rossi')).toBeInTheDocument());

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'r' } });

    await waitFor(() => expect(screen.getByTestId('search')).toBeEmptyDOMElement());
  });

  it('with a filter and no result it says so and offers to show everything', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([]) as never);
    renderPage('/quotations?status=EXPIRED');
    await waitFor(() => expect(screen.getByText('no_quotations_filtered')).toBeInTheDocument());
    expect(screen.queryByText('empty_title')).not.toBeInTheDocument();

    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'zzz' } });
    fireEvent.click(screen.getByText('quotations_show_all'));

    await waitFor(() => expect(screen.getByText('Mario Rossi')).toBeInTheDocument());
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();
    expect(screen.getByRole('searchbox')).toHaveValue('');
    expect(quotationService.searchQuotations).toHaveBeenLastCalledWith(listQuery());
  });

  it('without any filter an empty list is still the first-quotation invitation', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([]) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('empty_title')).toBeInTheDocument());
    expect(screen.queryByText('no_quotations_filtered')).not.toBeInTheDocument();
  });

  it('ignores the answer to an earlier search that arrives after the current one', async () => {
    let answerOldSearch: (value: unknown) => void = () => {};
    vi.mocked(quotationService.searchQuotations).mockImplementation(((params: { query?: string }) => {
      if (params.query === 'ro') return new Promise((resolve) => { answerOldSearch = resolve; });
      return Promise.resolve(page([params.query === 'rossi' ? DRAFT_QUOTATION : MULTI_OPTION_QUOTATION]));
    }) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('Luigi Verdi')).toBeInTheDocument());

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'ro' } });
    await waitFor(() => expect(quotationService.searchQuotations).toHaveBeenLastCalledWith(listQuery({ query: 'ro' })));
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'rossi' } });
    await waitFor(() => expect(screen.getByText('Mario Rossi')).toBeInTheDocument());

    // The slow answer for "ro" lands now, after the one for "rossi": it must not replace it.
    answerOldSearch(page([MULTI_OPTION_QUOTATION]));
    await new Promise((resolve) => { setTimeout(resolve, 50); });
    expect(screen.getByText('Mario Rossi')).toBeInTheDocument();
    expect(screen.queryByText('Luigi Verdi')).not.toBeInTheDocument();
  });

  it('shows the dates, the total and the status under the guest name for a phone', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([{ ...DRAFT_QUOTATION, sendFailed: true }]) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('Mario Rossi')).toBeInTheDocument());
    // In the table, once in the (phone-hidden) column and once under the name; the chip row is not the table.
    expect(within(screen.getByRole('table')).getAllByText('status_draft')).toHaveLength(2);
    expect(screen.getAllByText('send_failed_badge')).toHaveLength(2);
    expect(screen.getAllByText('€200.00')).toHaveLength(2);
  });

  it('shows a price range for a quotation with multiple options', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([MULTI_OPTION_QUOTATION]) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('Luigi Verdi')).toBeInTheDocument());
    expect(screen.getAllByText('price_range')).toHaveLength(2);
  });

  it('action_convert on a multi-option quotation navigates to detail instead of converting directly', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([MULTI_OPTION_QUOTATION]) as never);
    renderPage();
    await waitFor(() => expect(screen.getAllByRole('button', { name: /row_actions_for/ }).length).toBeGreaterThan(0));
    openRowMenu();
    fireEvent.click(screen.getByRole('menuitem', { name: 'action_convert' }));
    expect(mockNavigate).toHaveBeenCalledWith('/quotations/q2', { state: { openConvert: true } });
    expect(quotationService.convertToReservation).not.toHaveBeenCalled();
  });

  it('passes axe accessibility check', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    const { container } = renderPage();
    await waitFor(() => screen.getByText('Mario Rossi'));
    expect(await axe(container)).toHaveNoViolations();
  });

  it('shows locale-formatted dates and the result range', async () => {
    vi.mocked(quotationService.searchQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('Mario Rossi')).toBeInTheDocument());
    expect(screen.getByText('9/1/2026')).toBeInTheDocument();
    expect(screen.getByText('9/3/2026')).toBeInTheDocument();
    expect(screen.getByText('8/20/2026')).toBeInTheDocument();
    expect(screen.getByText('list_range_summary')).toBeInTheDocument();
  });
});
