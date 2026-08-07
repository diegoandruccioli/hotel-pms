import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { StayRow } from './StayRow';
import { getStatusTone } from './stayStatusTone';
import type { StayResponse } from '../../types/stay.types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const STAY_CHECKED_IN: StayResponse = {
  id: 's1',
  reservationId: 'r1',
  guestId: 'guest-1234-abcd',
  roomId: 'room-1234-abcd',
  status: 'CHECKED_IN',
  actualCheckInTime: '2026-03-15T14:00:00',
  createdAt: '',
  updatedAt: '',
  alloggiatiSent: false,
  alloggiatiSendFailed: false,
  invoiceCreationFailed: false,
  checkoutEmailFailed: false,
  roomNumber: '101',
  guestDisplayName: 'Doe John',
};

const formatDate = (d?: string) => d ?? '-';

const renderRow = (props: Partial<React.ComponentProps<typeof StayRow>> = {}) =>
  render(
    <table>
      <tbody>
        <StayRow
          stay={STAY_CHECKED_IN}
          onCheckOut={vi.fn()}
          checkingOut={null}
          onRetryInvoice={vi.fn()}
          retryingInvoice={null}
          onRetryCheckoutEmail={vi.fn()}
          retryingEmail={null}
          formatDate={formatDate}
          getStatusTone={getStatusTone}
          t={((key: string) => key) as never}
          onGuestClick={vi.fn()}
          {...props}
        />
      </tbody>
    </table>,
  );

describe('StayRow', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders room number and guest display name', () => {
    renderRow();
    expect(screen.getByText('101')).toBeInTheDocument();
    expect(screen.getByText('Doe John')).toBeInTheDocument();
  });

  it('falls back to a truncated id when room number / guest display name are missing', () => {
    renderRow({ stay: { ...STAY_CHECKED_IN, roomNumber: null, guestDisplayName: null } });
    expect(screen.getByText('room-123…')).toBeInTheDocument();
    expect(screen.getByText('guest-12…')).toBeInTheDocument();
  });

  it('calls onGuestClick with the guest display name when the guest link is clicked', () => {
    const onGuestClick = vi.fn();
    renderRow({ onGuestClick });
    fireEvent.click(screen.getByText('Doe John'));
    expect(onGuestClick).toHaveBeenCalledWith('Doe John');
  });

  it('shows the checkout button only when the stay is CHECKED_IN, and calls onCheckOut', () => {
    const onCheckOut = vi.fn();
    renderRow({ onCheckOut });
    fireEvent.click(screen.getByText('action_checkout'));
    expect(onCheckOut).toHaveBeenCalledWith(STAY_CHECKED_IN);
  });

  it('hides the checkout button for a checked-out stay', () => {
    renderRow({ stay: { ...STAY_CHECKED_IN, status: 'CHECKED_OUT' } });
    expect(screen.queryByText('action_checkout')).not.toBeInTheDocument();
  });

  it('shows the invoice-failed badge and calls onRetryInvoice', () => {
    const onRetryInvoice = vi.fn();
    renderRow({
      stay: { ...STAY_CHECKED_IN, invoiceCreationFailed: true, invoiceCreationFailureReason: 'BILLING_DOWN' },
      onRetryInvoice,
    });
    expect(screen.getByText('invoice_creation_failed')).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText('retry_invoice_creation'));
    expect(onRetryInvoice).toHaveBeenCalledWith(
      expect.objectContaining({ invoiceCreationFailed: true }),
    );
  });

  it('shows the checkout-email-failed badge and calls onRetryCheckoutEmail', () => {
    const onRetryCheckoutEmail = vi.fn();
    renderRow({
      stay: { ...STAY_CHECKED_IN, checkoutEmailFailed: true, checkoutEmailFailureReason: 'SMTP_DOWN' },
      onRetryCheckoutEmail,
    });
    expect(screen.getByText('checkout_email_failed')).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText('retry_checkout_email'));
    expect(onRetryCheckoutEmail).toHaveBeenCalledWith(
      expect.objectContaining({ checkoutEmailFailed: true }),
    );
  });

  it('disables the retry-invoice button while a retry is in flight', () => {
    renderRow({
      stay: { ...STAY_CHECKED_IN, invoiceCreationFailed: true },
      retryingInvoice: STAY_CHECKED_IN.id,
    });
    expect(screen.getByLabelText('retry_invoice_creation')).toBeDisabled();
  });

  it('passes axe accessibility check', async () => {
    const { container } = renderRow();
    expect(await axe(container)).toHaveNoViolations();
  }, 30000);
});
