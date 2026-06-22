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

const DELETABLE_STATUSES = new Set(['CONFIRMED', 'PENDING']);

type SortField = 'checkInDate' | 'checkOutDate' | 'status';
type SortDir = 'asc' | 'desc';

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

const getStatusLabel = (status: string, t: TFunction) =>
  t(`status_${status.toLowerCase()}`, status);

interface ReservationRowProps {
  reservation: ReservationResponse;
  rooms: RoomResponse[];
  onCheckIn: (reservationId: string, roomId: string, expectedGuests: number, guestId: string) => void;
  onView: (reservationId: string) => void;
  onEdit: (reservationId: string) => void;
  onDelete?: (id: string) => void;
  t: TFunction;
}

const ReservationRow = memo(({ reservation, rooms, onCheckIn, onView, onEdit, onDelete, t }: ReservationRowProps) => {
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

  const handleDeleteClick = useCallback(() => {
    onDelete?.(reservation.id);
  }, [onDelete, reservation.id]);

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
        <M3StatusChip label={getStatusLabel(reservation.status, t)} tone={getStatusTone(reservation.status)} />
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
        {onDelete && DELETABLE_STATUSES.has(reservation.status) && (
          <button
            type="button"
            aria-label={`${t('delete_reservation')} ${reservation.id}`}
            onClick={handleDeleteClick}
            className="text-error hover:text-error/80 font-medium text-sm ml-4"
          >
            {t('delete_reservation')}
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
  const [reservationToDelete, setReservationToDelete] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [sortField, setSortField] = useState<SortField>('checkInDate');
  const [sortDir, setSortDir] = useState<SortDir>('desc');

  useEffect(() => {
    const id = setTimeout(() => setDebouncedSearch(searchQuery), 300);
    return () => clearTimeout(id);
  }, [searchQuery]);

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
  }, []);

  const handleSortFieldChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    setSortField(e.target.value as SortField);
  }, []);

  const toggleSortDir = useCallback(() => {
    setSortDir((prev) => (prev === 'asc' ? 'desc' : 'asc'));
  }, []);

  const filteredReservations = useMemo(() => {
    const active = reservations.filter((r) => r.active !== false);
    const searched = !debouncedSearch.trim()
      ? active
      : active.filter((r) => r.guestFullName?.toLowerCase().includes(debouncedSearch.toLowerCase()));
    const sorted = [...searched].sort((a, b) => {
      const cmp = a[sortField].localeCompare(b[sortField]);
      return sortDir === 'asc' ? cmp : -cmp;
    });
    return sorted;
  }, [reservations, debouncedSearch, sortField, sortDir]);

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

  const handleDeleteRequest = useCallback((id: string) => {
    setReservationToDelete(id);
  }, []);

  const handleDeleteDialogClose = useCallback(() => {
    setReservationToDelete(null);
  }, []);

  const handleDeleteConfirm = useCallback(async () => {
    if (!reservationToDelete) return;
    setDeleting(true);
    try {
      await reservationService.deleteReservation(reservationToDelete);
      setReservations((prev) => prev.filter((r) => r.id !== reservationToDelete));
      addToast(t('reservation_deleted_success'), 'success');
    } catch {
      addToast(t('delete_reservation_failed'), 'error');
    } finally {
      setDeleting(false);
      setReservationToDelete(null);
    }
  }, [reservationToDelete, addToast, t]);

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
          <div className="flex items-center gap-2">
            <label htmlFor="reservations-sort-field" className="sr-only">{t('sort_by')}</label>
            <select
              id="reservations-sort-field"
              value={sortField}
              onChange={handleSortFieldChange}
              className="pl-3 pr-8 py-2 rounded-shape-xs border border-outline bg-transparent text-sm font-body text-on-surface focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none"
            >
              <option value="checkInDate">{t('check_in')}</option>
              <option value="checkOutDate">{t('check_out')}</option>
              <option value="status">{t('status')}</option>
            </select>
            <button
              type="button"
              onClick={toggleSortDir}
              aria-label={sortDir === 'asc' ? t('sort_dir_asc') : t('sort_dir_desc')}
              className="flex items-center justify-center w-10 h-10 rounded-shape-full border border-outline text-on-surface-variant hover:bg-primary/[0.08] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 transition-colors"
            >
              <MaterialIcon name={sortDir === 'asc' ? 'arrow_upward' : 'arrow_downward'} size={20} />
            </button>
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
                onDelete={isAdminOrOwner ? handleDeleteRequest : undefined}
                t={t}
              />
            ))
          )}
        </M3Table>
      )}
      {reservationToDelete && (
        <M3Dialog
          open
          title={t('delete_reservation')}
          titleId="confirm-delete-reservation-dialog"
          onClose={handleDeleteDialogClose}
        >
          <p className="text-sm font-body text-on-surface">{t('delete_reservation_confirm')}</p>
          <div className="flex justify-end gap-3 pt-4">
            <M3Button type="button" variant="outlined" onClick={handleDeleteDialogClose} disabled={deleting}>
              {t('cancel')}
            </M3Button>
            <M3Button type="button" onClick={handleDeleteConfirm} loading={deleting}>
              {t('confirm')}
            </M3Button>
          </div>
        </M3Dialog>
      )}
    </div>
  );
};
