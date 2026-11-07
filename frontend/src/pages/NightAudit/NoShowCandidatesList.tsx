import { memo, useCallback, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Alert } from '../../components/Alert';
import {
  M3ConfirmDialog, M3EmptyState, M3LoadingState, M3RowActionsMenu, M3StatusChip, M3Table, M3TableActionLink, M3TableCell, M3TableRow, type RowAction,
} from '../../components/m3';
import { useFormatters } from '../../hooks';
import { useRoomsLookup, useUpdateReservationStatus } from '../../hooks/queries';
import { queryKeys } from '../../lib';
import { useToastStore } from '../../store';
import type { ReservationResponse, RoomResponse } from '../../types';
import { getErrorMessage, reservationStatusTone, todayIsoDate } from '../../utils';
import { useNoShowCandidates } from './useNoShowCandidates';

type ResolveStatus = 'NO_SHOW' | 'CANCELLED';

interface PendingResolution {
  reservation: ReservationResponse;
  status: ResolveStatus;
}

interface CandidateRowProps {
  reservation: ReservationResponse;
  room: RoomResponse | undefined;
  onResolve: (reservation: ReservationResponse, status: ResolveStatus) => void;
}

const CandidateRow = memo(({ reservation, room, onResolve }: CandidateRowProps) => {
  const { t } = useTranslation('common');
  const { formatDate } = useFormatters();
  const navigate = useNavigate();
  const name = reservation.guestFullName ?? reservation.guestId;

  const handleNoShow = useCallback(() => onResolve(reservation, 'NO_SHOW'), [onResolve, reservation]);
  const handleCancel = useCallback(() => onResolve(reservation, 'CANCELLED'), [onResolve, reservation]);
  const handleEdit = useCallback(() => navigate(`/reservations/edit/${reservation.id}`), [navigate, reservation.id]);
  const handleCheckIn = useCallback(() => {
    navigate(`/stays/check-in/${reservation.id}`, {
      state: {
        roomId: reservation.lineItems?.[0]?.roomId ?? '',
        expectedGuests: reservation.expectedGuests,
        guestId: reservation.guestId,
      },
    });
  }, [navigate, reservation]);

  const items = useMemo<RowAction[]>(() => [
    { id: 'edit', label: t('edit'), icon: 'edit', onSelect: handleEdit },
    { id: 'no-show', label: t('mark_no_show'), icon: 'person_off', tone: 'error', onSelect: handleNoShow },
    { id: 'cancel', label: t('cancel_reservation'), icon: 'event_busy', tone: 'error', onSelect: handleCancel },
  ], [t, handleEdit, handleNoShow, handleCancel]);

  return (
    <M3TableRow>
      <M3TableCell>
        <span className="font-medium">{name}</span>
        <span className="block text-xs text-on-surface-variant">
          {formatDate(reservation.checkInDate)} → {formatDate(reservation.checkOutDate)}
        </span>
      </M3TableCell>
      <M3TableCell>{room?.roomNumber ?? '—'}</M3TableCell>
      <M3TableCell>
        <M3StatusChip
          label={t(`status_${reservation.status.toLowerCase()}`, reservation.status)}
          tone={reservationStatusTone[reservation.status]}
        />
      </M3TableCell>
      <M3TableCell>
        <div className="flex items-center justify-end gap-1">
          {reservation.status === 'CONFIRMED' && reservation.checkOutDate > todayIsoDate() && (
            <M3TableActionLink aria-label={`${t('check_in')} ${name}`} onClick={handleCheckIn}>{t('check_in')}</M3TableActionLink>
          )}
          <M3RowActionsMenu label={t('row_actions_for', { name })} items={items} />
        </div>
      </M3TableCell>
    </M3TableRow>
  );
});
CandidateRow.displayName = 'CandidateRow';

/**
 * Step 1 of the night audit: the arrivals the run would sweep up as no-shows, each
 * resolvable on its own (no-show, cancel, edit the dates, or check the guest in late).
 */
export const NoShowCandidatesList = ({ businessDate }: { businessDate: string }) => {
  const { t } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);
  const { candidates, total, isLoading, error } = useNoShowCandidates(businessDate);
  const { data: rooms } = useRoomsLookup();
  const queryClient = useQueryClient();
  const statusMutation = useUpdateReservationStatus();
  const [pending, setPending] = useState<PendingResolution | null>(null);

  const roomsById = useMemo(() => new Map((rooms ?? []).map((r) => [r.id, r])), [rooms]);
  const headers = useMemo(() => [t('guest'), t('label_room'), t('status'), t('actions')], [t]);

  const handleResolve = useCallback((reservation: ReservationResponse, status: ResolveStatus) => {
    setPending({ reservation, status });
  }, []);
  const handleDialogClose = useCallback(() => setPending(null), []);

  const handleConfirm = useCallback(async () => {
    if (!pending) return;
    const { reservation, status } = pending;
    const isNoShow = status === 'NO_SHOW';
    const failure = t(isNoShow ? 'mark_no_show_failed' : 'cancel_reservation_failed');
    // Explicit null check: a never-updated reservation's @Version is 0, which is valid.
    if (reservation.version === null || reservation.version === undefined) {
      addToast(failure, 'error');
      setPending(null);
      return;
    }
    try {
      await statusMutation.mutateAsync({ id: reservation.id, status, version: reservation.version });
      addToast(t(isNoShow ? 'no_show_marked_success' : 'reservation_cancelled_success'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, failure), 'error');
      // A rejected change (stale version, stay already created) leaves the row out of date: reload it.
      void queryClient.invalidateQueries({ queryKey: queryKeys.reservations.all });
    } finally {
      setPending(null);
    }
  }, [pending, statusMutation, queryClient, addToast, t]);

  if (isLoading) return <M3LoadingState label={t('loading')} />;
  if (error) {
    return (
      <Alert tone="error">
        {t('night_audit_noshows_load_failed')} {getErrorMessage(error, '')}
      </Alert>
    );
  }
  if (candidates.length === 0) {
    return <M3EmptyState icon="task_alt" title={t('night_audit_noshows_empty')} />;
  }

  const hidden = total - candidates.length;
  const isNoShowPending = pending?.status === 'NO_SHOW';

  return (
    <div className="space-y-3">
      <M3Table headers={headers} stickyEnd>
        {candidates.map((reservation) => (
          <CandidateRow
            key={reservation.id}
            reservation={reservation}
            room={roomsById.get(reservation.lineItems?.[0]?.roomId ?? '')}
            onResolve={handleResolve}
          />
        ))}
      </M3Table>
      {hidden > 0 && (
        <p className="text-sm font-body text-on-surface-variant">
          {t('night_audit_noshows_more', { count: hidden })}{' '}
          <Link to="/reservations" className="underline font-medium">{t('view_all')}</Link>
        </p>
      )}
      {pending && (
        <M3ConfirmDialog
          title={t(isNoShowPending ? 'mark_no_show' : 'cancel_reservation')}
          titleId="night-audit-resolve-dialog"
          message={t(isNoShowPending ? 'mark_no_show_confirm' : 'cancel_reservation_confirm')}
          onConfirm={handleConfirm}
          onCancel={handleDialogClose}
          loading={statusMutation.isPending}
        />
      )}
    </div>
  );
};
