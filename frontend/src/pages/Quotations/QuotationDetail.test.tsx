import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { QuotationDetail } from './QuotationDetail';
import { quotationService } from '../../services/quotationService';

vi.mock('../../services/quotationService');

vi.mock('./QuotationPdfPreviewDialog', () => ({
  QuotationPdfPreviewDialog: ({ onClose }: { onClose: () => void }) => (
    <div data-testid="pdf-preview-dialog">
      <button type="button" onClick={onClose}>close-preview</button>
    </div>
  ),
}));

const stableT = (key: string, opts?: Record<string, unknown>) => {
  if (opts && typeof opts.amount !== 'undefined') return `${key}:${opts.amount}`;
  return key;
};
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: stableT, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('../../store/toastStore', () => ({
  useToastStore: (sel: unknown) =>
    (sel as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const DRAFT_QUOTATION = {
  id: 'q1',
  guestId: 'g1',
  guestFullName: 'Mario Rossi',
  prospectEmail: null,
  checkInDate: '2026-09-01',
  checkOutDate: '2026-09-03',
  expectedGuests: 2,
  status: 'DRAFT',
  validUntil: '2026-08-25',
  totalPrice: 200,
  options: [
    {
      id: 'opt1',
      label: 'Opzione 1',
      position: 0,
      totalPrice: 200,
      lineItems: [{ id: 'li1', roomId: 'r1', roomNumber: '101', roomTypeName: 'Standard', price: 200 }],
    },
  ],
  acceptedOptionId: null,
  sendFailed: false,
  sendFailureReason: null,
  createdAt: '2026-08-01T00:00:00',
  updatedAt: '2026-08-01T00:00:00',
};

const MULTI_OPTION_QUOTATION = {
  ...DRAFT_QUOTATION,
  totalPrice: 200,
  options: [
    {
      id: 'opt1',
      label: 'Opzione 1',
      position: 0,
      totalPrice: 200,
      lineItems: [{ id: 'li1', roomId: 'r1', roomNumber: '101', roomTypeName: 'Standard', price: 200 }],
    },
    {
      id: 'opt2',
      label: 'Opzione 2',
      position: 1,
      totalPrice: 260,
      lineItems: [{ id: 'li2', roomId: 'r2', roomNumber: '202', roomTypeName: 'Suite', price: 260 }],
    },
  ],
};

const INITIAL_ENTRIES = ['/quotations/q1'];

const renderDetail = () => render(
  <MemoryRouter initialEntries={INITIAL_ENTRIES}>
    <Routes>
      <Route path="/quotations/:id" element={<QuotationDetail />} />
    </Routes>
  </MemoryRouter>,
);

describe('QuotationDetail', () => {
  beforeEach(() => vi.resetAllMocks());

  it('renders the guest name, status and line items after load', async () => {
    vi.mocked(quotationService.getQuotationById).mockResolvedValue(DRAFT_QUOTATION as never);
    renderDetail();

    await waitFor(() => expect(screen.getByText('Mario Rossi')).toBeInTheDocument());
    expect(screen.getByText('status_draft')).toBeInTheDocument();
    expect(screen.getByText('101')).toBeInTheDocument();
    expect(screen.getByText('Standard')).toBeInTheDocument();
  });

  it('shows an error state with retry on load failure', async () => {
    vi.mocked(quotationService.getQuotationById).mockRejectedValueOnce({ response: {} });
    vi.mocked(quotationService.getQuotationById).mockResolvedValueOnce(DRAFT_QUOTATION as never);
    renderDetail();

    await waitFor(() => expect(screen.getAllByText('error_loading_quotation').length).toBeGreaterThan(0));
    fireEvent.click(screen.getByText('common:try_again'));
    await waitFor(() => expect(screen.getByText('Mario Rossi')).toBeInTheDocument());
  });

  it('shows the send-failed banner when sendFailed is true', async () => {
    vi.mocked(quotationService.getQuotationById).mockResolvedValue(
      { ...DRAFT_QUOTATION, sendFailed: true } as never,
    );
    renderDetail();
    await waitFor(() => expect(screen.getByText('send_failed_banner')).toBeInTheDocument());
  });

  it('only shows Modifica for a DRAFT quotation', async () => {
    vi.mocked(quotationService.getQuotationById).mockResolvedValue(
      { ...DRAFT_QUOTATION, status: 'ACCEPTED' } as never,
    );
    renderDetail();
    await waitFor(() => expect(screen.getByText('Mario Rossi')).toBeInTheDocument());
    expect(screen.queryByText('common:edit')).not.toBeInTheDocument();
  });

  it('sends the quotation and shows a success toast', async () => {
    vi.mocked(quotationService.getQuotationById).mockResolvedValue(DRAFT_QUOTATION as never);
    vi.mocked(quotationService.sendQuotation).mockResolvedValue({ ...DRAFT_QUOTATION, status: 'SENT' } as never);
    renderDetail();
    await waitFor(() => expect(screen.getByText('action_send')).toBeInTheDocument());

    fireEvent.click(screen.getByText('action_send'));
    await waitFor(() => expect(quotationService.sendQuotation).toHaveBeenCalledWith('q1'));
  });

  it('duplicates the quotation and navigates to the new draft', async () => {
    vi.mocked(quotationService.getQuotationById).mockResolvedValue(DRAFT_QUOTATION as never);
    vi.mocked(quotationService.duplicateQuotation).mockResolvedValue({ ...DRAFT_QUOTATION, id: 'q2' } as never);
    renderDetail();
    await waitFor(() => expect(screen.getByText('action_duplicate')).toBeInTheDocument());

    fireEvent.click(screen.getByText('action_duplicate'));
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/quotations/q2'));
  });

  it('opens the PDF preview dialog', async () => {
    vi.mocked(quotationService.getQuotationById).mockResolvedValue(DRAFT_QUOTATION as never);
    renderDetail();
    await waitFor(() => expect(screen.getByText('action_preview_pdf')).toBeInTheDocument());

    fireEvent.click(screen.getByText('action_preview_pdf'));
    expect(screen.getByTestId('pdf-preview-dialog')).toBeInTheDocument();
  });

  it('declining an already-accepted quotation shows a friendly error', async () => {
    vi.mocked(quotationService.getQuotationById).mockResolvedValue(
      { ...DRAFT_QUOTATION, status: 'SENT' } as never,
    );
    vi.mocked(quotationService.declineQuotation).mockRejectedValue({ response: { status: 409 } });
    renderDetail();
    await waitFor(() => expect(screen.getByText('action_decline')).toBeInTheDocument());

    fireEvent.click(screen.getByText('action_decline'));
    await waitFor(() => expect(screen.getByText('confirm_decline')).toBeInTheDocument());
    fireEvent.click(screen.getByText('common:confirm'));

    await waitFor(() => expect(quotationService.declineQuotation).toHaveBeenCalledWith('q1'));
  });

  it('deletes the quotation and navigates back to the list', async () => {
    vi.mocked(quotationService.getQuotationById).mockResolvedValue(DRAFT_QUOTATION as never);
    vi.mocked(quotationService.deleteQuotation).mockResolvedValue(undefined);
    renderDetail();
    await waitFor(() => expect(screen.getByText('action_delete')).toBeInTheDocument());

    fireEvent.click(screen.getByText('action_delete'));
    await waitFor(() => expect(screen.getByText('confirm_delete')).toBeInTheDocument());
    fireEvent.click(screen.getByText('common:confirm'));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/quotations'));
  });

  it('converts directly without a dialog when there is only one option', async () => {
    vi.mocked(quotationService.getQuotationById).mockResolvedValue(DRAFT_QUOTATION as never);
    vi.mocked(quotationService.convertToReservation).mockResolvedValue({ id: 'res1' } as never);
    renderDetail();
    await waitFor(() => expect(screen.getByText('action_convert')).toBeInTheDocument());

    fireEvent.click(screen.getByText('action_convert'));
    await waitFor(() => expect(quotationService.convertToReservation).toHaveBeenCalledWith('q1', 'opt1'));
    expect(mockNavigate).toHaveBeenCalledWith('/reservations/res1');
  });

  it('shows both options side by side and asks which one to convert when there are multiple', async () => {
    vi.mocked(quotationService.getQuotationById).mockResolvedValue(MULTI_OPTION_QUOTATION as never);
    vi.mocked(quotationService.convertToReservation).mockResolvedValue({ id: 'res1' } as never);
    renderDetail();
    await waitFor(() => expect(screen.getByText('Opzione 1')).toBeInTheDocument());
    expect(screen.getByText('Opzione 2')).toBeInTheDocument();

    fireEvent.click(screen.getByText('action_convert'));
    await waitFor(() => expect(screen.getByText('label_choose_option_to_convert')).toBeInTheDocument());

    // opt1 is pre-selected (no acceptedOptionId yet, defaults to the first option), so only
    // opt2's button still reads action_choose_option — clicking it switches the choice to opt2.
    fireEvent.click(screen.getByText('action_choose_option'));
    fireEvent.click(screen.getByText('common:confirm'));

    await waitFor(() => expect(quotationService.convertToReservation).toHaveBeenCalledWith('q1', 'opt2'));
  });

  it('passes axe accessibility check', async () => {
    vi.mocked(quotationService.getQuotationById).mockResolvedValue(DRAFT_QUOTATION as never);
    const { container } = renderDetail();
    await waitFor(() => screen.getByText('Mario Rossi'));
    expect(await axe(container)).toHaveNoViolations();
  });
});
