import { useCallback, useMemo } from 'react';
import type { TFunction } from 'i18next';
import type { StayResponse } from '../../types';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button, M3RowActionsMenu, M3StatusChip, M3TableActionLink, type RowAction } from '../../components/m3';

export const GuestCell = ({ stay, onGuestClick }: { stay: StayResponse; onGuestClick: (name: string) => void }) => {
  const handleClick = useCallback(() => {
    onGuestClick(stay.guestDisplayName ?? stay.guestId);
  }, [onGuestClick, stay.guestDisplayName, stay.guestId]);

  return (
    <M3TableActionLink onClick={handleClick} className="truncate block max-w-[120px] text-left" title={stay.guestId}>
      {stay.guestDisplayName ?? `${stay.guestId.substring(0, 8)}…`}
    </M3TableActionLink>
  );
};

interface AlloggiatiCellProps {
  stay: StayResponse;
  onRetryInvoice: (s: StayResponse) => void;
  retryingInvoice: string | null;
  onRetryCheckoutEmail: (s: StayResponse) => void;
  retryingEmail: string | null;
  t: TFunction;
}

export const AlloggiatiCell = ({ stay, onRetryInvoice, retryingInvoice, onRetryCheckoutEmail, retryingEmail, t }: AlloggiatiCellProps) => {
  const handleRetryInvoice = useCallback(() => onRetryInvoice(stay), [onRetryInvoice, stay]);
  const handleRetryCheckoutEmail = useCallback(() => onRetryCheckoutEmail(stay), [onRetryCheckoutEmail, stay]);

  return (
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
            className="flex items-center justify-center w-10 h-10 rounded-shape-full text-error hover:bg-error/12 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-error disabled:opacity-50"
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
            className="flex items-center justify-center w-10 h-10 rounded-shape-full text-error hover:bg-error/12 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-error disabled:opacity-50"
          >
            <MaterialIcon name={retryingEmail === stay.id ? 'progress_activity' : 'refresh'} size={16} />
          </button>
        </span>
      )}
    </div>
  );
};

export const GuestsCountCell = ({ stay, onManageGuests }: { stay: StayResponse; onManageGuests: (stayId: string) => void }) => {
  const handleClick = useCallback(() => onManageGuests(stay.id), [onManageGuests, stay.id]);
  const count = stay.guests?.length || 0;

  if (stay.status !== 'CHECKED_IN') {
    return (
      <div className="font-medium flex items-center gap-1.5 text-on-surface">
        <MaterialIcon name="group" size={18} />
        <span>{count}</span>
      </div>
    );
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      className="font-medium flex items-center gap-1.5 text-primary hover:underline focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-shape-xs"
    >
      <MaterialIcon name="group" size={18} />
      <span>{count}</span>
    </button>
  );
};

export const ActionsCell = ({ stay, onCheckOut, checkingOut, onExtend, onChangeRoom, t }: {
  stay: StayResponse;
  onCheckOut: (s: StayResponse) => void;
  checkingOut: string | null;
  onExtend: (s: StayResponse) => void;
  onChangeRoom: (s: StayResponse) => void;
  t: TFunction;
}) => {
  const handleCheckOut = useCallback(() => onCheckOut(stay), [onCheckOut, stay]);
  const handleExtend = useCallback(() => onExtend(stay), [onExtend, stay]);
  const handleChangeRoom = useCallback(() => onChangeRoom(stay), [onChangeRoom, stay]);

  const items = useMemo<RowAction[]>(() => [
    { id: 'extend', label: t('action_extend_stay'), icon: 'event_repeat', onSelect: handleExtend, testId: `extend-btn-${stay.id}` },
    { id: 'change-room', label: t('action_change_room'), icon: 'meeting_room', onSelect: handleChangeRoom, testId: `change-room-btn-${stay.id}` },
  ], [t, handleExtend, handleChangeRoom, stay.id]);

  if (stay.status !== 'CHECKED_IN') return null;

  return (
    <div className="flex items-center justify-end gap-2">
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
      <M3RowActionsMenu
        label={t('row_actions_for', { name: stay.guestDisplayName ?? stay.guestId })}
        items={items}
      />
    </div>
  );
};
