import { useCallback, memo } from 'react';
import type { TFunction } from 'i18next';
import type { StayResponse, StayStatus } from '../../types/stay.types';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3/M3Button';
import { M3TableRow, M3TableCell } from '../../components/m3/M3Table';
import { M3StatusChip } from '../../components/m3/M3StatusChip';
import { M3TableActionLink } from '../../components/m3/M3TableActionLink';

// ---------------------------------------------------------------------------
// StayRow — one row of the Stays table (room/guest/dates/status/actions).
// ---------------------------------------------------------------------------
export interface StayRowProps {
  stay: StayResponse;
  onCheckOut: (s: StayResponse) => void;
  checkingOut: string | null;
  onRetryInvoice: (s: StayResponse) => void;
  retryingInvoice: string | null;
  onRetryCheckoutEmail: (s: StayResponse) => void;
  retryingEmail: string | null;
  formatDate: (d?: string) => string;
  getStatusTone: (s: StayStatus) => 'success' | 'neutral' | 'info';
  t: TFunction;
  onGuestClick: (guestDisplayName: string) => void;
}

export const StayRow = memo(({
  stay, onCheckOut, checkingOut, onRetryInvoice, retryingInvoice, onRetryCheckoutEmail, retryingEmail,
  formatDate, getStatusTone, t, onGuestClick,
}: StayRowProps) => {
  const handleCheckOut = useCallback(() => {
    onCheckOut(stay);
  }, [onCheckOut, stay]);

  const handleRetryInvoice = useCallback(() => {
    onRetryInvoice(stay);
  }, [onRetryInvoice, stay]);

  const handleRetryCheckoutEmail = useCallback(() => {
    onRetryCheckoutEmail(stay);
  }, [onRetryCheckoutEmail, stay]);

  const handleGuestNameClick = useCallback(() => {
    onGuestClick(stay.guestDisplayName ?? stay.guestId);
  }, [onGuestClick, stay.guestDisplayName, stay.guestId]);

  return (
    <M3TableRow key={stay.id}>
      <M3TableCell className="font-medium">
        <span className="truncate block max-w-[120px]" title={stay.roomId}>
          {stay.roomNumber ?? `${stay.roomId.substring(0, 8)}…`}
        </span>
      </M3TableCell>
      <M3TableCell className="text-on-surface-variant">
        <M3TableActionLink
          onClick={handleGuestNameClick}
          className="truncate block max-w-[120px] text-left"
          title={stay.guestId}
        >
          {stay.guestDisplayName ?? `${stay.guestId.substring(0, 8)}…`}
        </M3TableActionLink>
      </M3TableCell>
      <M3TableCell className="text-on-surface-variant">{formatDate(stay.actualCheckInTime)}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{formatDate(stay.actualCheckOutTime)}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{stay.expectedCheckOutDate ?? '-'}</M3TableCell>
      <M3TableCell>
        <div className="font-medium flex items-center gap-1.5 text-on-surface">
          <MaterialIcon name="group" size={18} />
          <span>{stay.guests?.length || 0}</span>
        </div>
      </M3TableCell>
      <M3TableCell>
        <M3StatusChip
          label={t(`status_${stay.status.toLowerCase()}`, stay.status.replace('_', ' '))}
          tone={getStatusTone(stay.status)}
        />
      </M3TableCell>
      <M3TableCell>
        <div className="flex flex-col items-start gap-1">
          <span title={stay.alloggiatiSendFailed ? stay.alloggiatiFailureReason ?? undefined : undefined}>
            <M3StatusChip
              label={
                stay.alloggiatiSent
                  ? t('alloggiati_sent')
                  : stay.alloggiatiSendFailed
                    ? t('alloggiati_failed')
                    : t('alloggiati_not_sent')
              }
              tone={stay.alloggiatiSent ? 'success' : stay.alloggiatiSendFailed ? 'error' : 'neutral'}
            />
          </span>
          {stay.invoiceCreationFailed && (
            <span className="inline-flex items-center gap-1" title={stay.invoiceCreationFailureReason ?? undefined}>
              <M3StatusChip label={t('invoice_creation_failed')} tone="error" />
              <button
                type="button"
                onClick={handleRetryInvoice}
                disabled={retryingInvoice === stay.id}
                aria-label={t('retry_invoice_creation')}
                className="flex items-center justify-center w-10 h-10 rounded-shape-full text-error hover:bg-error/[0.12] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error disabled:opacity-50"
              >
                <MaterialIcon name={retryingInvoice === stay.id ? 'progress_activity' : 'refresh'} size={16} />
              </button>
            </span>
          )}
          {stay.checkoutEmailFailed && (
            <span className="inline-flex items-center gap-1" title={stay.checkoutEmailFailureReason ?? undefined}>
              <M3StatusChip label={t('checkout_email_failed')} tone="error" />
              <button
                type="button"
                onClick={handleRetryCheckoutEmail}
                disabled={retryingEmail === stay.id}
                aria-label={t('retry_checkout_email')}
                className="flex items-center justify-center w-10 h-10 rounded-shape-full text-error hover:bg-error/[0.12] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error disabled:opacity-50"
              >
                <MaterialIcon name={retryingEmail === stay.id ? 'progress_activity' : 'refresh'} size={16} />
              </button>
            </span>
          )}
        </div>
      </M3TableCell>
      <M3TableCell className="text-right">
        {stay.status === 'CHECKED_IN' && (
          <M3Button
            variant="tonal"
            icon={checkingOut === stay.id ? 'progress_activity' : 'logout'}
            loading={checkingOut === stay.id}
            disabled={checkingOut === stay.id}
            onClick={handleCheckOut}
            id={`checkout-btn-${stay.id}`}
            className="text-xs h-10 px-3"
          >
            {t('action_checkout')}
          </M3Button>
        )}
      </M3TableCell>
    </M3TableRow>
  );
});
StayRow.displayName = 'StayRow';
