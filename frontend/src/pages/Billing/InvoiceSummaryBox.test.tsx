import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { InvoiceSummaryBox } from './InvoiceSummaryBox';
import { formatCurrency } from '../../utils';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const INVOICE = { invoiceNumber: 'INV-007', totalAmount: 180 };

describe('InvoiceSummaryBox', () => {
  it('shows the invoice number and the formatted total', () => {
    render(<InvoiceSummaryBox invoice={INVOICE} />);
    expect(screen.getByText('INV-007')).toBeInTheDocument();
    expect(screen.getByText(formatCurrency(180, 'en'))).toBeInTheDocument();
  });

  it('passes axe accessibility check', async () => {
    const { container } = render(<InvoiceSummaryBox invoice={INVOICE} />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
