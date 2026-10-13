import { useCallback, useMemo } from 'react';
import type { TFunction } from 'i18next';
import type { ReservationResponse, RoomResponse } from '../../types';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Avatar, M3StatusChip, M3TableActionLink } from '../../components/m3';
import { cn, todayIsoDate, reservationStatusTone } from '../../utils';

const DELETABLE_STATUSES = new Set(['CONFIRMED', 'PENDING']);
// Mirrors ReservationServiceImpl.ALLOWED_TRANSITIONS's NO_SHOW edges — a
// client-side pre-filter so the action isn't offered for a request that
// would always 409 server-side; the backend re-validates authoritatively
// (including the check-in-date-passed / no-existing-stay guards this list
// can't express).
const NO_SHOW_ELIGIBLE_STATUSES = new Set(['CONFIRMED', 'PENDING']);

const getStatusLabel = (status: string, t: TFunction) =>
  t(`status_${status.toLowerCase()}`, status);

export const RoomsCell = ({ reservation, rooms }: { reservation: ReservationResponse; rooms: RoomResponse[] }) => {
  const roomNumbers = useMemo(() => (
    reservation.lineItems?.filter(li => li.active !== false).map(li => {
      const room = rooms.find(r => r.id === li.roomId);
      return room?.roomNumber;
    }).filter(Boolean).sort().join(', ') || '-'
  ), [reservation.lineItems, rooms]);

  return <span className="text-on-surface-variant font-medium">{roomNumbers}</span>;
};

export const GuestsCountCell = ({ reservation }: { reservation: ReservationResponse }) => (
  <div className={cn(
    'font-medium flex items-center gap-1.5',
    (reservation.actualGuests || 0) < reservation.expectedGuests ? 'text-secondary' :
    (reservation.actualGuests || 0) > reservation.expectedGuests ? 'text-error' :
    'text-on-surface'
  )}>
    <MaterialIcon name="group" size={18} />
    <span>{reservation.actualGuests || 0} / {reservation.expectedGuests}</span>
  </div>
);

interface StatusCellProps {
  reservation: ReservationResponse;
  onRetryConfirmationEmail: (id: string) => void;
  retryingEmail: string | null;
  t: TFunction;
}

export const StatusCell = ({ reservation, onRetryConfirmationEmail, retryingEmail, t }: StatusCellProps) => {
  const handleRetry = useCallback(() => {
    onRetryConfirmationEmail(reservation.id);
  }, [onRetryConfirmationEmail, reservation.id]);

  return (
    <div className="flex flex-col items-start gap-1">
      <M3StatusChip label={getStatusLabel(reservation.status, t)} tone={reservationStatusTone[reservation.status]} />
      {reservation.confirmationEmailFailed && (
        <span
          className="inline-flex items-center gap-1"
          title={reservation.confirmationEmailFailureReason ?? undefined}
        >
          <M3StatusChip label={t('confirmation_email_failed')} tone="error" />
          <button
            type="button"
            onClick={handleRetry}
            disabled={retryingEmail === reservation.id}
            aria-label={t('retry_confirmation_email')}
            className="flex items-center justify-center w-10 h-10 rounded-shape-full text-error hover:bg-error/12 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-error disabled:opacity-50"
          >
            <MaterialIcon name={retryingEmail === reservation.id ? 'progress_activity' : 'refresh'} size={16} />
          </button>
        </span>
      )}
    </div>
  );
};

interface ActionsCellProps {
  reservation: ReservationResponse;
  onCheckIn: (reservationId: string, roomId: string, expectedGuests: number, guestId: string) => void;
  onView: (reservationId: string) => void;
  onEdit: (reservationId: string) => void;
  onDelete?: (id: string) => void;
  onMarkNoShow?: (id: string) => void;
  t: TFunction;
}

const isNoShowEligible = (reservation: ReservationResponse): boolean =>
  NO_SHOW_ELIGIBLE_STATUSES.has(reservation.status) && reservation.checkInDate <= todayIsoDate();

export const ActionsCell = ({ reservation, onCheckIn, onView, onEdit, onDelete, onMarkNoShow, t }: ActionsCellProps) => {
  const handleCheckInClick = useCallback(() => {
    onCheckIn(
      reservation.id,
      reservation.lineItems?.[0]?.roomId || '',
      reservation.expectedGuests,
      reservation.guestId,
    );
  }, [onCheckIn, reservation]);

  const handleViewClick = useCallback(() => {
    onView(reservation.id);
  }, [onView, reservation.id]);

  const handleEditClick = useCallback(() => {
    onEdit(reservation.id);
  }, [onEdit, reservation.id]);

  const handleDeleteClick = useCallback(() => {
    onDelete?.(reservation.id);
  }, [onDelete, reservation.id]);

  const handleMarkNoShowClick = useCallback(() => {
    onMarkNoShow?.(reservation.id);
  }, [onMarkNoShow, reservation.id]);

  return (
    <div className="flex max-w-72 flex-wrap justify-end gap-x-4 gap-y-1">
      {reservation.status === 'CONFIRMED' && (
        <M3TableActionLink
          data-testid={`check-in-btn-${reservation.id}`}
          onClick={handleCheckInClick}
        >
          {t('check_in')}
        </M3TableActionLink>
      )}
      <M3TableActionLink onClick={handleViewClick}>
        {t('view')}
      </M3TableActionLink>
      <M3TableActionLink onClick={handleEditClick}>
        {t('edit')}
      </M3TableActionLink>
      {onMarkNoShow && isNoShowEligible(reservation) && (
        <M3TableActionLink
          tone="error"
          aria-label={`${t('mark_no_show')} ${reservation.id}`}
          onClick={handleMarkNoShowClick}
        >
          {t('mark_no_show')}
        </M3TableActionLink>
      )}
      {onDelete && DELETABLE_STATUSES.has(reservation.status) && (
        <M3TableActionLink
          tone="error"
          aria-label={`${t('delete_reservation')} ${reservation.id}`}
          onClick={handleDeleteClick}
        >
          {t('delete_reservation')}
        </M3TableActionLink>
      )}
    </div>
  );
};

/** Guest column: initial avatar and full name. */
export const GuestNameCell = ({ name }: { name?: string }) => (
  <div className="flex items-center gap-3">
    <M3Avatar name={name} size="md" aria-hidden="true" />
    <span className="font-medium">{name}</span>
  </div>
);
