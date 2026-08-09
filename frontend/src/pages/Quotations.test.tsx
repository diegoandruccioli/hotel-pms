import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { MemoryRouter } from 'react-router-dom';

const mockNavigate = vi.hoisted(() => vi.fn());
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});
import { Quotations } from './Quotations';
import { quotationService } from '../services/quotationService';
import { useToastStore } from '../store/toastStore';

vi.mock('react-i18next', () => {
  const t = (key: string, opts?: Record<string, unknown>) => {
    if (opts && typeof opts.amount !== 'undefined') return `${key}:${opts.amount}`;
    return key;
  };
  return {
    useTranslation: () => ({ t, i18n: { language: 'en' } }),
    initReactI18next: { type: '3rdParty', init: vi.fn() },
  };
});

vi.mock('../services/quotationService', () => ({
  quotationService: {
    getAllQuotations: vi.fn(),
    sendQuotation: vi.fn(),
    convertToReservation: vi.fn(),
    declineQuotation: vi.fn(),
    deleteQuotation: vi.fn(),
    downloadPdf: vi.fn(),
  },
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
  lineItems: [],
  sendFailed: false,
  sendFailureReason: null,
  createdAt: '2026-08-01T00:00:00',
  updatedAt: '2026-08-01T00:00:00',
};

const page = (content: unknown[], totalPages = 1) => ({ content, totalPages, totalElements: content.length });

const renderPage = () => render(<MemoryRouter><Quotations /></MemoryRouter>);

describe('Quotations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useToastStore).mockImplementation((sel: unknown) =>
      (sel as (s: { addToast: typeof mockAddToast }) => unknown)({ addToast: mockAddToast }));
  });

  it('renders heading', async () => {
    vi.mocked(quotationService.getAllQuotations).mockResolvedValue(page([]) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('title')).toBeInTheDocument());
  });

  it('renders a quotation row after data loads', async () => {
    vi.mocked(quotationService.getAllQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('Mario Rossi')).toBeInTheDocument());
    expect(screen.getByText('€ 200.00')).toBeInTheDocument();
  });

  it('renders empty state when no quotations', async () => {
    vi.mocked(quotationService.getAllQuotations).mockResolvedValue(page([]) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('no_quotations_found')).toBeInTheDocument());
  });

  it('renders error state on load failure', async () => {
    vi.mocked(quotationService.getAllQuotations).mockRejectedValue(new Error('fail'));
    renderPage();
    await waitFor(() => expect(screen.getAllByText('error_loading_quotations').length).toBeGreaterThan(0));
  });

  it('new_quotation button navigates to /quotations/new', async () => {
    vi.mocked(quotationService.getAllQuotations).mockResolvedValue(page([]) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('new_quotation')).toBeInTheDocument());
    fireEvent.click(screen.getByText('new_quotation'));
    expect(mockNavigate).toHaveBeenCalledWith('/quotations/new');
  });

  it('action_send calls sendQuotation and shows success toast', async () => {
    vi.mocked(quotationService.getAllQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    vi.mocked(quotationService.sendQuotation).mockResolvedValue({ ...DRAFT_QUOTATION, status: 'SENT', sendFailed: false } as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('action_send')).toBeInTheDocument());
    fireEvent.click(screen.getByText('action_send'));
    await waitFor(() => expect(quotationService.sendQuotation).toHaveBeenCalledWith('q1'));
    expect(mockAddToast).toHaveBeenCalledWith('toast_sent', 'success');
  });

  it('action_send shows error toast when send fails', async () => {
    vi.mocked(quotationService.getAllQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    vi.mocked(quotationService.sendQuotation).mockResolvedValue({ ...DRAFT_QUOTATION, sendFailed: true } as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('action_send')).toBeInTheDocument());
    fireEvent.click(screen.getByText('action_send'));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('toast_send_failed', 'error'));
  });

  it('action_download_pdf calls quotationService.downloadPdf', async () => {
    vi.mocked(quotationService.getAllQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    renderPage();
    await waitFor(() => expect(screen.getByText('action_download_pdf')).toBeInTheDocument());
    fireEvent.click(screen.getByText('action_download_pdf'));
    expect(quotationService.downloadPdf).toHaveBeenCalledWith('q1');
  });

  it('action_delete shows confirmation dialog then deletes', async () => {
    vi.mocked(quotationService.getAllQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    vi.mocked(quotationService.deleteQuotation).mockResolvedValue(undefined);
    renderPage();
    await waitFor(() => expect(screen.getByText('action_delete')).toBeInTheDocument());
    fireEvent.click(screen.getByText('action_delete'));
    expect(screen.getByText('confirm_delete')).toBeInTheDocument();
    fireEvent.click(screen.getByText('common:confirm'));
    await waitFor(() => expect(quotationService.deleteQuotation).toHaveBeenCalledWith('q1'));
  });

  it('passes axe accessibility check', async () => {
    vi.mocked(quotationService.getAllQuotations).mockResolvedValue(page([DRAFT_QUOTATION]) as never);
    const { container } = renderPage();
    await waitFor(() => screen.getByText('Mario Rossi'));
    expect(await axe(container)).toHaveNoViolations();
  });
});
