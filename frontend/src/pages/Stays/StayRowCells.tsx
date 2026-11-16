import { useCallback, useMemo } from 'react';
import type { TFunction } from 'i18next';
import type { StayResponse } from '../../types';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button, M3RowActionsMenu, M3StatusChip, M3TableActionLink, type RowAction } from '../../components/m3';
import { stayStatusTone } from '../../utils';

interface GuestCellProps {
  stay: StayResponse;
  onGuestClick: (guestId: string) => void;
  className?: string;
}

export const GuestCell = ({ stay, onGuestClick, className = 'max-w-[120px]' }: GuestCellProps) => {
  const handleClick = useCallback(() => {
    onGuestClick(stay.guestId);
  }, [onGuestClick, stay.guestId]);

  return (
    <M3TableActionLink onClick={handleClick} className={`truncate block text-left ${className}`} title={stay.guestId}>
      {stay.guestDisplayName ?? `${stay.guestId.substring(0, 8)}…`}
    </M3TableActionLink>
  );
};

export const StayStatusChip = ({ stay, t }: { stay: StayResponse; t: TFunction }) => (
  <M3StatusChip
    label={t(`status_${stay.status.toLowerCase()}`, stay.status.replace('_', ' '))}
    tone={stayStatusTone[stay.status]}
  />
);

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
            className="flex items-center justify-center size-11 sm:pointer-fine:size-10 rounded-shape-full text-error hover:bg-error/12 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-error disabled:opacity-50"
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
            className="flex items-center justify-center size-11 sm:pointer-fine:size-10 rounded-shape-full text-error hover:bg-error/12 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-error disabled:opacity-50"
          >
            <MaterialIcon name={retryingEmail === stay.id ? 'progress_activity' : 'refresh'} size={16} />
          </button>
        </span>
      )}
    </div>
  );
};

/**
 * Guests of the stay. For a stay still in house only those who have not left yet count: a guest who
 * left early (own `departureDate`) is no longer in the room, and the number tells the receptionist who
 * is there now. A finished stay counts everyone who stayed.
 */
export const GuestsCountCell = ({ stay, onManageGuests, t }: {
  stay: StayResponse;
  onManageGuests: (stayId: string) => void;
  t: TFunction;
}) => {
  const handleClick = useCallback(() => onManageGuests(stay.id), [onManageGuests, stay.id]);
  const all = stay.guests ?? [];
  const inHouse = stay.status === 'CHECKED_IN';
  const departed = inHouse ? all.filter((g) => g.departureDate).length : 0;
  const count = all.length - departed;
  const hint = departed > 0 ? t('stays_guests_departed', { count: departed }) : undefined;

  if (!inHouse) {
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
      title={hint}
      className="font-medium flex items-center gap-1.5 text-primary hover:underline focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-shape-xs"
    >
      <MaterialIcon name="group" size={18} />
      <span>{count}</span>
      {hint && <span className="sr-only">{hint}</span>}
    </button>
  );
};

export const ActionsCell = ({ stay, onCheckOut, checkingOut, onExtend, onChangeRoom, onOpenReservation, onOpenDetail, t }: {
  stay: StayResponse;
  onCheckOut: (s: StayResponse) => void;
  checkingOut: string | null;
  onExtend: (s: StayResponse) => void;
  onChangeRoom: (s: StayResponse) => void;
  onOpenReservation: (reservationId: string) => void;
  onOpenDetail: (stayId: string) => void;
  t: TFunction;
}) => {
  const handleCheckOut = useCallback(() => onCheckOut(stay), [onCheckOut, stay]);
  const handleExtend = useCallback(() => onExtend(stay), [onExtend, stay]);
  const handleChangeRoom = useCallback(() => onChangeRoom(stay), [onChangeRoom, stay]);
  const handleOpenReservation = useCallback(
    () => onOpenReservation(stay.reservationId),
    [onOpenReservation, stay.reservationId],
  );
  const handleOpenDetail = useCallback(() => onOpenDetail(stay.id), [onOpenDetail, stay.id]);
  const checkedIn = stay.status === 'CHECKED_IN';

  const items = useMemo<RowAction[]>(() => {
    const list: RowAction[] = [
      { id: 'detail', label: t('stays_open_detail'), icon: 'hotel', onSelect: handleOpenDetail, testId: `detail-btn-${stay.id}` },
    ];
    // A walk-in stay has no reservation to open.
    if (stay.reservationId) {
      list.push({
        id: 'reservation', label: t('stays_open_reservation'), icon: 'event',
        onSelect: handleOpenReservation, testId: `reservation-btn-${stay.id}`,
      });
    }
    if (checkedIn) {
      list.push(
        { id: 'extend', label: t('action_extend_stay'), icon: 'event_repeat', onSelect: handleExtend, testId: `extend-btn-${stay.id}` },
        { id: 'change-room', label: t('action_change_room'), icon: 'meeting_room', onSelect: handleChangeRoom, testId: `change-room-btn-${stay.id}` },
      );
    }
    return list;
  }, [t, checkedIn, stay.reservationId, stay.id, handleOpenDetail, handleOpenReservation, handleExtend, handleChangeRoom]);

  return (
    <div className="flex items-center justify-end gap-2">
      {checkedIn && (
        <M3Button
          variant="tonal"
          icon={checkingOut === stay.id ? 'progress_activity' : 'logout'}
          loading={checkingOut === stay.id}
          disabled={checkingOut === stay.id}
          onClick={handleCheckOut}
          id={`checkout-btn-${stay.id}`}
          className="text-xs px-3"
        >
          {t('action_checkout')}
        </M3Button>
      )}
      <M3RowActionsMenu
        label={t('row_actions_for', { name: stay.guestDisplayName ?? stay.guestId })}
        items={items}
      />
    </div>
  );
};

interface PhoneSummaryProps extends AlloggiatiCellProps {
  onGuestClick: (guestId: string) => void;
  formatDate: (value?: string | null) => string;
}

/**
 * Under `md` the table keeps only the room and the row actions as columns, so what a receptionist reads
 * first (who, when, state) sits under the room number instead of behind a sideways scroll. The
 * Alloggiati chip and the retry buttons only appear here when something went wrong; the full column
 * is back from `md` up.
 */
export const StayPhoneSummary = ({ onGuestClick, formatDate, ...alloggiati }: PhoneSummaryProps) => {
  const { stay, t } = alloggiati;
  const hasFailure = stay.alloggiatiSendFailed || stay.invoiceCreationFailed || stay.checkoutEmailFailed;

  return (
    <div className="flex flex-col items-start gap-1.5 pb-1 font-normal whitespace-normal text-on-surface-variant md:hidden">
      <GuestCell stay={stay} onGuestClick={onGuestClick} className="max-w-52 px-0" />
      <span>{formatDate(stay.actualCheckInTime)} – {formatDate(stay.expectedCheckOutDate)}</span>
      <StayStatusChip stay={stay} t={t} />
      {hasFailure && <AlloggiatiCell {...alloggiati} />}
    </div>
  );
};
