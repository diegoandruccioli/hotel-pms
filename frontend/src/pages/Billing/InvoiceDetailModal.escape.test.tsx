import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { InvoiceDetailModal } from './InvoiceDetailModal';
import type { InvoiceResponse } from '../../types';
import { billingService } from '../../services';

// Real M3Dialog / M3ConfirmDialog here (the sibling test file stubs M3Dialog): this is about how
// Escape behaves when dialogs are stacked, which only exists with the real dialog stack.
vi.mock('../../services/billingService', () => ({
  billingService: {
    downloadPdf: vi.fn(),
    validateFatturaPAXml: vi.fn(),
    downloadFatturaPAXml: vi.fn(),
    updateDocumentType: vi.fn(),
    addCharge: vi.fn(),
    removeCharge: vi.fn(),
  },
}));

vi.mock('../../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const INVOICE_WITH_EXTRA_CHARGE: InvoiceResponse = {
  id: 'inv1', invoiceNumber: 'INV-001', issueDate: '2026-01-01T10:00:00',
  totalAmount: 20, status: 'ISSUED', documentType: 'FATTURA', sdiStatus: 'NOT_SENT',
  reservationId: 'res1', guestId: 'g1', stayId: 's1', payments: [],
  charges: [{ id: 'c2', type: 'EXTRA' as const, description: 'Minibar', amount: 20 }],
};

const pressEscape = () => fireEvent.keyDown(document, { key: 'Escape' });

describe('InvoiceDetailModal Escape with a nested dialog', () => {
  const onClose = vi.fn();

  beforeEach(() => vi.clearAllMocks());

  it('closes only the remove-charge confirmation on the first Escape, the invoice on the second', async () => {
    render(<InvoiceDetailModal invoice={INVOICE_WITH_EXTRA_CHARGE} onClose={onClose} />);
    fireEvent.click(screen.getByRole('button', { name: /remove_charge/i }));
    expect(screen.getByText('confirm_remove_charge')).toBeInTheDocument();

    pressEscape();

    await waitFor(() => expect(screen.queryByText('confirm_remove_charge')).not.toBeInTheDocument());
    expect(onClose).not.toHaveBeenCalled();
    expect(billingService.removeCharge).not.toHaveBeenCalled();
    expect(screen.getByRole('dialog', { name: 'invoice_detail_title' })).toBeInTheDocument();

    pressEscape();

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('closes the invoice dialog with Escape when nothing is stacked on it', () => {
    render(<InvoiceDetailModal invoice={INVOICE_WITH_EXTRA_CHARGE} onClose={onClose} />);

    pressEscape();

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
