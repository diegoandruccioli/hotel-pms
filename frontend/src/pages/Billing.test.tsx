import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { Billing } from './Billing';
import { billingService } from '../services/billingService';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/billingService', () => ({
  billingService: { getAllInvoices: vi.fn() },
}));

describe('Billing', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should show loading spinner initially', () => {
    vi.mocked(billingService.getAllInvoices).mockReturnValue(new Promise(() => {}));
    render(<Billing />);
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should render invoices on success', async () => {
    vi.mocked(billingService.getAllInvoices).mockResolvedValueOnce([
      { id: '1', invoiceNumber: 'INV-001', issueDate: '2026-03-01', totalAmount: 500, status: 'PAID' },
    ] as never);

    render(<Billing />);

    await waitFor(() => {
      expect(screen.getByText('INV-001')).toBeInTheDocument();
    });
  });

  it('should show empty state when no invoices', async () => {
    vi.mocked(billingService.getAllInvoices).mockResolvedValueOnce([] as never);
    render(<Billing />);

    await waitFor(() => {
      expect(screen.getByText('no_invoices')).toBeInTheDocument();
    });
  });

  it('should show error on failure', async () => {
    vi.mocked(billingService.getAllInvoices).mockRejectedValueOnce(new Error('Network error'));
    render(<Billing />);

    await waitFor(() => {
      expect(screen.getByText('error_loading_invoices')).toBeInTheDocument();
    });
  });
});
