import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { AddChargeModal } from './AddChargeModal';
import { billingService } from '../../services';
import { mockAxiosErrorWithDetail } from '../../test-utils';

const mockAddToast = vi.hoisted(() => vi.fn());

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/billingService', () => ({
  billingService: { addCharge: vi.fn() },
}));

vi.mock('../../store/toastStore', () => ({
  useToastStore: (sel: unknown) =>
    (sel as (s: { addToast: typeof mockAddToast }) => unknown)({ addToast: mockAddToast }),
}));

vi.mock('../../components/m3/M3Dialog', () => ({
  M3Dialog: ({ children, title }: { children: React.ReactNode; title: string }) => (
    <div role="dialog" aria-label={title}>{children}</div>
  ),
}));

const INVOICE = {
  id: 'inv1', invoiceNumber: 'INV-001', issueDate: '2026-01-01T00:00:00',
  totalAmount: 150, status: 'ISSUED' as const, documentType: 'FATTURA' as const, sdiStatus: 'NOT_SENT' as const,
  reservationId: 'res1', guestId: 'g1', stayId: 's1', payments: [], charges: [],
};

const CHARGE_RESPONSE = {
  id: 'c1', invoiceId: 'inv1', type: 'EXTRA' as const, description: 'charge_preset_minibar',
  amount: 15, vatRate: 0.22,
};

describe('AddChargeModal', () => {
  const onClose = vi.fn();
  const onAdded = vi.fn();

  beforeEach(() => vi.clearAllMocks());

  it('renders invoice details', () => {
    render(<AddChargeModal invoice={INVOICE} stayId="s1" onClose={onClose} onAdded={onAdded} />);
    expect(screen.getByText('INV-001')).toBeInTheDocument();
  });

  it('defaults to the minibar preset with no free-text field shown', () => {
    render(<AddChargeModal invoice={INVOICE} stayId="s1" onClose={onClose} onAdded={onAdded} />);
    expect(screen.queryByText('charge_description_custom')).not.toBeInTheDocument();
  });

  it('shows the free-text field when the "other" preset is selected', () => {
    render(<AddChargeModal invoice={INVOICE} stayId="s1" onClose={onClose} onAdded={onAdded} />);
    fireEvent.change(screen.getByLabelText('charge_description', { exact: false }), { target: { value: 'OTHER' } });
    expect(screen.getByLabelText('charge_description_custom *')).toBeInTheDocument();
  });

  it('requires a description when "other" is selected and left blank', async () => {
    render(<AddChargeModal invoice={INVOICE} stayId="s1" onClose={onClose} onAdded={onAdded} />);
    fireEvent.change(screen.getByLabelText('charge_description', { exact: false }), { target: { value: 'OTHER' } });
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '15' } });
    fireEvent.submit(document.querySelector('form')!);
    await waitFor(() => expect(screen.getByText('error_charge_description_required')).toBeInTheDocument());
    expect(billingService.addCharge).not.toHaveBeenCalled();
  });

  it('shows validation error for an invalid amount', async () => {
    render(<AddChargeModal invoice={INVOICE} stayId="s1" onClose={onClose} onAdded={onAdded} />);
    fireEvent.submit(document.querySelector('form')!);
    await waitFor(() => expect(screen.getByText('error_invalid_amount')).toBeInTheDocument());
    expect(billingService.addCharge).not.toHaveBeenCalled();
  });

  it('calls addCharge with the preset description and onAdded on successful submission', async () => {
    vi.mocked(billingService.addCharge).mockResolvedValue(CHARGE_RESPONSE);
    render(<AddChargeModal invoice={INVOICE} stayId="s1" onClose={onClose} onAdded={onAdded} />);

    const amountInputs = screen.getAllByRole('spinbutton');
    fireEvent.change(amountInputs[0], { target: { value: '15' } });
    fireEvent.submit(document.querySelector('form')!);

    await waitFor(() => expect(onAdded).toHaveBeenCalledOnce());
    expect(billingService.addCharge).toHaveBeenCalledWith('s1', {
      type: 'EXTRA',
      description: 'charge_preset_minibar',
      amount: 15,
    });
    expect(onAdded).toHaveBeenCalledWith({
      ...INVOICE,
      charges: [CHARGE_RESPONSE],
      totalAmount: 165,
    });
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('submits the free-text description when "other" is selected', async () => {
    vi.mocked(billingService.addCharge).mockResolvedValue({ ...CHARGE_RESPONSE, description: 'Parking - long stay' });
    render(<AddChargeModal invoice={INVOICE} stayId="s1" onClose={onClose} onAdded={onAdded} />);

    fireEvent.change(screen.getByLabelText('charge_description', { exact: false }), { target: { value: 'OTHER' } });
    fireEvent.change(screen.getByLabelText('charge_description_custom *'), {
      target: { value: 'Parking - long stay' },
    });
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '20' } });
    fireEvent.submit(document.querySelector('form')!);

    await waitFor(() => expect(billingService.addCharge).toHaveBeenCalledWith('s1', {
      type: 'EXTRA',
      description: 'Parking - long stay',
      amount: 20,
    }));
  });

  it('calls onClose when cancel clicked', () => {
    render(<AddChargeModal invoice={INVOICE} stayId="s1" onClose={onClose} onAdded={onAdded} />);
    fireEvent.click(screen.getByText('cancel'));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('passes axe accessibility check', async () => {
    const { container } = render(
      <AddChargeModal invoice={INVOICE} stayId="s1" onClose={onClose} onAdded={onAdded} />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('shows the backend detail (e.g. invoice locked after export) instead of the generic fallback', async () => {
    vi.mocked(billingService.addCharge).mockRejectedValue(
      mockAxiosErrorWithDetail('INVOICE_LOCKED_AFTER_EXPORT', 409),
    );
    render(<AddChargeModal invoice={INVOICE} stayId="s1" onClose={onClose} onAdded={onAdded} />);
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '15' } });
    fireEvent.submit(document.querySelector('form')!);
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('INVOICE_LOCKED_AFTER_EXPORT', 'error'));
    expect(onAdded).not.toHaveBeenCalled();
  });

  it('falls back to the generic message when the error carries no detail', async () => {
    vi.mocked(billingService.addCharge).mockRejectedValue(new Error('network blip'));
    render(<AddChargeModal invoice={INVOICE} stayId="s1" onClose={onClose} onAdded={onAdded} />);
    fireEvent.change(screen.getByRole('spinbutton'), { target: { value: '15' } });
    fireEvent.submit(document.querySelector('form')!);
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('charge_add_failed', 'error'));
  });
});
