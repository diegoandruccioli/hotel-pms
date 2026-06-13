import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import { useNavigate } from 'react-router-dom';
import { reservationService } from '../services/reservationService';
import type { ReservationResponse } from '../types/reservation.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3Table, M3TableRow, M3TableCell } from '../components/m3/M3Table';
import { M3StatusChip } from '../components/m3/M3StatusChip';
import { M3Dialog } from '../components/m3/M3Dialog';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { inventoryService } from '../services/inventoryService';
import type { RoomResponse } from '../types/inventory.types';
import { useAuthStore } from '../store/authStore';
import { useToastStore } from '../store/toastStore';

const CANCELLABLE_STATUSES = new Set(['CONFIRMED', 'PENDING']);

const getStatusTone = (status: string) => {
  switch (status.toUpperCase()) {
    case 'CONFIRMED': return 'success' as const;
    case 'CHECKED_IN': return 'success' as const;
    case 'PARTIALLY_CHECKED_IN': return 'warning' as const;
    case 'PENDING': return 'warning' as const;
    case 'CANCELLED': return 'error' as const;
    default: return 'neutral' as const;
  }
};

interface ReservationRowProps {
  reservation: ReservationResponse;
  rooms: RoomResponse[];
  onCheckIn: (reservationId: string, roomId: string, expectedGuests: number, guestId: string) => void;
  onView: (reservationId: string) => void;
  onEdit: (reservationId: string) => void;
  onCancel?: (id: string) => void;
  t: TFunction;
}

const ReservationRow = memo(({ reservation, rooms, onCheckIn, onView, onEdit, onCancel, t }: ReservationRowProps) => {
  const roomNumbers = useMemo(() => {
    return reservation.lineItems?.filter(li => li.active !== false).map(li => {
      const room = rooms.find(r => r.id === li.roomId);
      return room?.roomNumber;
    }).filter(Boolean).sort().join(', ') || '-';
  }, [reservation.lineItems, rooms]);

  const handleCheckInClick = useCallback(() => {
    onCheckIn(
      reservation.id, 
      reservation.lineItems?.[0]?.roomId || '',
      reservation.expectedGuests,
      reservation.guestId
    );
  }, [onCheckIn, reservation]);

  const handleViewClick = useCallback(() => {
    onView(reservation.id);
  }, [onView, reservation.id]);

  const handleEditClick = useCallback(() => {
    onEdit(reservation.id);
  }, [onEdit, reservation.id]);

  const handleCancelClick = useCallback(() => {
    onCancel?.(reservation.id);
  }, [onCancel, reservation.id]);

  return (
    <M3TableRow>
      <M3TableCell className="font-medium">{reservation.guestFullName}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{reservation.checkInDate}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{reservation.checkOutDate}</M3TableCell>
      <M3TableCell className="text-on-surface-variant font-medium">
        {roomNumbers}
      </M3TableCell>
      <M3TableCell>
        <div className={`font-medium flex items-center gap-1.5 ${
          (reservation.actualGuests || 0) < reservation.expectedGuests ? 'text-warning' :
          (reservation.actualGuests || 0) > reservation.expectedGuests ? 'text-error' :
          'text-on-surface'
        }`}>
          <MaterialIcon name="group" size={18} />
          <span>{reservation.actualGuests || 0} / {reservation.expectedGuests}</span>
        </div>
      </M3TableCell>
      <M3TableCell>
        <M3StatusChip label={reservation.status} tone={getStatusTone(reservation.status)} />
      </M3TableCell>
      <M3TableCell className="text-right">
        {reservation.status === 'CONFIRMED' && (
          <button 
            className="text-primary hover:text-primary/80 font-medium text-sm mr-4"
            onClick={handleCheckInClick}
          >
            {t('check_in')}
          </button>
        )}
        <button 
          className="text-primary hover:text-primary/80 font-medium text-sm mr-4"
          onClick={handleViewClick}
        >
          {t('view')}
        </button>
        <button
          type="button"
          className="text-primary hover:text-primary/80 font-medium text-sm"
          onClick={handleEditClick}
        >
          {t('edit')}
        </button>
        {onCancel && CANCELLABLE_STATUSES.has(reservation.status) && (
          <button
            type="button"
            aria-label={`${t('cancel_reservation')} ${reservation.id}`}
            onClick={handleCancelClick}
            className="text-error hover:text-error/80 font-medium text-sm ml-4"
          >
            {t('cancel_reservation')}
          </button>
        )}
      </M3TableCell>
    </M3TableRow>
  );
});

ReservationRow.displayName = 'ReservationRow';

export const Reservations = () => {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const role = useAuthStore((s) => s.user?.role);
  const isAdminOrOwner = role === 'ADMIN' || role === 'OWNER';

  const [reservations, setReservations] = useState<ReservationResponse[]>([]);
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reservationToCancel, setReservationToCancel] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');

  useEffect(() => {
    const id = setTimeout(() => setDebouncedSearch(searchQuery), 300);
    return () => clearTimeout(id);
  }, [searchQuery]);

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
  }, []);

  const filteredReservations = useMemo(() => {
    const active = reservations.filter((r) => r.active !== false);
    if (!debouncedSearch.trim()) return active;
    const q = debouncedSearch.toLowerCase();
    return active.filter((r) => r.guestFullName?.toLowerCase().includes(q));
  }, [reservations, debouncedSearch]);

  const loadReservations = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const [resData, roomsData] = await Promise.all([
        reservationService.getAllReservations(),
        inventoryService.getAllRooms(0, 500)
      ]);
      setReservations(resData);
      setRooms(roomsData.content);
    } catch (err: unknown) {
      const e = err as {response?: {data?: {detail?: string}}, message?: string};
      setError(e.response?.data?.detail || e.message || t('failed_load_reservations'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    loadReservations();
  }, [loadReservations]);

  const handleNewReservation = useCallback(() => {
    navigate('/reservations/new');
  }, [navigate]);

  const handleCheckIn = useCallback((reservationId: string, roomId: string, expectedGuests: number, guestId: string) => {
    navigate(`/stays/check-in/${reservationId}`, {
      state: { roomId, expectedGuests, guestId }
    });
  }, [navigate]);

  const handleView = useCallback((reservationId: string) => {
    navigate(`/reservations/${reservationId}`);
  }, [navigate]);

  const handleEdit = useCallback((reservationId: string) => {
    navigate(`/reservations/edit/${reservationId}`);
  }, [navigate]);

  const handleCancelRequest = useCallback((id: string) => {
    setReservationToCancel(id);
  }, []);

  const handleCancelDialogClose = useCallback(() => {
    setReservationToCancel(null);
  }, []);

  const handleCancelConfirm = useCallback(async () => {
    if (!reservationToCancel) return;
    setCancelling(true);
    try {
      await reservationService.cancelReservation(reservationToCancel);
      setReservations((prev) =>
        prev.map((r) => r.id === reservationToCancel ? { ...r, status: 'CANCELLED' } : r),
      );
      addToast(t('reservation_cancelled_success'), 'success');
    } catch {
      addToast(t('cancel_reservation_failed'), 'error');
    } finally {
      setCancelling(false);
      setReservationToCancel(null);
    }
  }, [reservationToCancel, addToast, t]);

  const tableHeaders = useMemo(() => [
    t('guest_name'), 
    t('check_in'), 
    t('check_out'), 
    t('nav_rooms'), 
    t('guests'), 
    t('status'), 
    <span key="sr" className="sr-only">{t('actions')}</span>
  ], [t]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center">
            <MaterialIcon name="event" className="mr-2 text-primary" />
            {t('nav_reservations')}
          </h1>
          <p className="text-sm font-body text-on-surface-variant mt-1">{t('reservations_subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="relative">
            <MaterialIcon name="search" size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant pointer-events-none" />
            <input
              type="search"
              value={searchQuery}
              onChange={handleSearchChange}
              placeholder={t('search_placeholder')}
              aria-label={t('search_placeholder')}
              className="pl-9 pr-3 py-2 w-full sm:w-56 rounded-shape-xs border border-outline bg-transparent text-sm font-body text-on-surface placeholder:text-on-surface-variant focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none"
            />
          </div>
          <M3Button data-testid="new-reservation-btn" icon="add" onClick={handleNewReservation}>
            {t('new_reservation')}
          </M3Button>
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center items-center h-64 bg-surface rounded-shape-md shadow-elevation-1">
          <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
        </div>
      ) : error ? (
        <div className="flex items-center gap-3 px-4 py-4 rounded-shape-sm bg-error-container text-on-error-container">
          <MaterialIcon name="error" size={20} className="flex-shrink-0" />
          <div>
            <h3 className="text-sm font-medium font-body">{t('error_loading_reservations')}</h3>
            <p className="mt-1 text-sm font-body opacity-80">{error}</p>
            <button type="button" onClick={loadReservations} className="mt-2 text-sm font-medium underline hover:no-underline">
              {t('try_again')}
            </button>
          </div>
        </div>
      ) : (
        <M3Table headers={tableHeaders}>
          {filteredReservations.length === 0 ? (
            <tr><td colSpan={6} className="py-8 text-center text-sm font-body text-on-surface-variant">{t('no_reservations_found')}</td></tr>
          ) : (
            filteredReservations.map((reservation) => (
              <ReservationRow
                key={reservation.id}
                reservation={reservation}
                rooms={rooms}
                onCheckIn={handleCheckIn}
                onView={handleView}
                onEdit={handleEdit}
                onCancel={isAdminOrOwner ? handleCancelRequest : undefined}
                t={t}
              />
            ))
          )}
        </M3Table>
      )}
      {reservationToCancel && (
        <M3Dialog
          open
          title={t('cancel_reservation')}
          titleId="confirm-cancel-reservation-dialog"
          onClose={handleCancelDialogClose}
        >
          <p className="text-sm font-body text-on-surface">{t('cancel_reservation_confirm')}</p>
          <div className="flex justify-end gap-3 pt-4">
            <M3Button type="button" variant="outlined" onClick={handleCancelDialogClose} disabled={cancelling}>
              {t('cancel')}
            </M3Button>
            <M3Button type="button" onClick={handleCancelConfirm} loading={cancelling}>
              {t('cancel_reservation')}
            </M3Button>
          </div>
        </M3Dialog>
      )}
    </div>
  );
};
