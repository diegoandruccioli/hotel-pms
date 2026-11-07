import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { ReservationResponse } from '../../types';
import { useDeleteReservation, useUpdateReservationStatus } from '../../hooks/queries';
import { useToastStore } from '../../store';
import { getErrorMessage } from '../../utils';

/** Delete and mark-no-show confirmations for the reservations list: which row is pending and the confirmed mutations. */
export function useReservationRowActions(reservations: ReservationResponse[]) {
  const { t } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);
  const [reservationToDelete, setReservationToDelete] = useState<string | null>(null);
  const [reservationToMarkNoShow, setReservationToMarkNoShow] = useState<string | null>(null);

  const handleDeleteRequest = useCallback((id: string) => {
    setReservationToDelete(id);
  }, []);

  const handleDeleteDialogClose = useCallback(() => {
    setReservationToDelete(null);
  }, []);

  const deleteReservationMutation = useDeleteReservation();
  const deleting = deleteReservationMutation.isPending;

  const handleDeleteConfirm = useCallback(async () => {
    if (!reservationToDelete) return;
    try {
      await deleteReservationMutation.mutateAsync(reservationToDelete);
      addToast(t('reservation_deleted_success'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('delete_reservation_failed')), 'error');
    } finally {
      setReservationToDelete(null);
    }
  }, [reservationToDelete, addToast, t, deleteReservationMutation]);

  const handleMarkNoShowRequest = useCallback((id: string) => {
    setReservationToMarkNoShow(id);
  }, []);

  const handleMarkNoShowDialogClose = useCallback(() => {
    setReservationToMarkNoShow(null);
  }, []);

  const updateReservationStatusMutation = useUpdateReservationStatus();
  const markingNoShow = updateReservationStatusMutation.isPending;

  const handleMarkNoShowConfirm = useCallback(async () => {
    if (!reservationToMarkNoShow) return;
    const reservation = reservations.find((r) => r.id === reservationToMarkNoShow);
    // Explicit null/undefined check, not a truthiness check: a
    // never-yet-updated reservation's @Version starts at 0, a valid value
    // that a falsy check would wrongly reject.
    if (reservation?.version === null || reservation?.version === undefined) {
      addToast(t('mark_no_show_failed'), 'error');
      setReservationToMarkNoShow(null);
      return;
    }
    try {
      await updateReservationStatusMutation.mutateAsync({
        id: reservationToMarkNoShow,
        status: 'NO_SHOW',
        version: reservation.version,
      });
      addToast(t('no_show_marked_success'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('mark_no_show_failed')), 'error');
    } finally {
      setReservationToMarkNoShow(null);
    }
  }, [reservationToMarkNoShow, reservations, addToast, t, updateReservationStatusMutation]);

  return {
    reservationToDelete,
    deleting,
    handleDeleteRequest,
    handleDeleteDialogClose,
    handleDeleteConfirm,
    reservationToMarkNoShow,
    markingNoShow,
    handleMarkNoShowRequest,
    handleMarkNoShowDialogClose,
    handleMarkNoShowConfirm,
  };
}
