import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import type { ColumnDef, SortingState } from '@tanstack/react-table';
import type { ReservationResponse } from '../types/reservation.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3DataTable } from '../components/m3/M3DataTable';
import { M3TableActionLink } from '../components/m3/M3TableActionLink';
import { M3StatusChip } from '../components/m3/M3StatusChip';
import { M3Dialog } from '../components/m3/M3Dialog';
import { M3LoadingState } from '../components/m3/M3LoadingState';
import { M3ErrorState } from '../components/m3/M3ErrorState';
import { M3Pagination } from '../components/m3/M3Pagination';
import { M3TextField } from '../components/m3/M3TextField';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import type { RoomResponse } from '../types/inventory.types';
import { useAuthStore } from '../store/authStore';
import { useToastStore } from '../store/toastStore';
import { getErrorMessage } from '../utils/errorMessage';
import {
  useReservationsSearch,
  useRoomsLookup,
  useDeleteReservation,
  useRetryConfirmationEmail,
} from '../hooks/queries/useReservations';

const DELETABLE_STATUSES = new Set(['CONFIRMED', 'PENDING']);
const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;
const EMPTY_RESERVATIONS: ReservationResponse[] = [];
const EMPTY_ROOMS: RoomResponse[] = [];

type SortField = 'checkInDate' | 'checkOutDate' | 'status';
type SortDir = 'asc' | 'desc';
const DEFAULT_SORT_FIELD: SortField = 'checkInDate';
const DEFAULT_SORT_DIR: SortDir = 'desc';

interface ReservationsNavState {
  upcomingOnly?: boolean;
  sortField?: SortField;
  sortDir?: SortDir;
}

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

const RoomsCell = ({ reservation, rooms }: { reservation: ReservationResponse; rooms: RoomResponse[] }) => {
  const roomNumbers = useMemo(() => (
    reservation.lineItems?.filter(li => li.active !== false).map(li => {
      const room = rooms.find(r => r.id === li.roomId);
      return room?.roomNumber;
    }).filter(Boolean).sort().join(', ') || '-'
  ), [reservation.lineItems, rooms]);

  return <span className="text-on-surface-variant font-medium">{roomNumbers}</span>;
};

const GuestsCountCell = ({ reservation }: { reservation: ReservationResponse }) => (
  <div className={`font-medium flex items-center gap-1.5 ${
    (reservation.actualGuests || 0) < reservation.expectedGuests ? 'text-warning' :
    (reservation.actualGuests || 0) > reservation.expectedGuests ? 'text-error' :
    'text-on-surface'
  }`}>
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

const StatusCell = ({ reservation, onRetryConfirmationEmail, retryingEmail, t }: StatusCellProps) => {
  const handleRetry = useCallback(() => {
    onRetryConfirmationEmail(reservation.id);
  }, [onRetryConfirmationEmail, reservation.id]);

  return (
    <div className="flex flex-col items-start gap-1">
      <M3StatusChip label={getStatusLabel(reservation.status, t)} tone={getStatusTone(reservation.status)} />
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
            className="flex items-center justify-center w-10 h-10 rounded-shape-full text-error hover:bg-error/[0.12] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error disabled:opacity-50"
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
  t: TFunction;
}

const ActionsCell = ({ reservation, onCheckIn, onView, onEdit, onDelete, t }: ActionsCellProps) => {
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

  return (
    <div className="text-right">
      {reservation.status === 'CONFIRMED' && (
        <M3TableActionLink
          className="mr-4"
          data-testid={`check-in-btn-${reservation.id}`}
          onClick={handleCheckInClick}
        >
          {t('check_in')}
        </M3TableActionLink>
      )}
      <M3TableActionLink className="mr-4" onClick={handleViewClick}>
        {t('view')}
      </M3TableActionLink>
      <M3TableActionLink onClick={handleEditClick}>
        {t('edit')}
      </M3TableActionLink>
      {onDelete && DELETABLE_STATUSES.has(reservation.status) && (
        <M3TableActionLink
          tone="error"
          className="ml-4"
          aria-label={`${t('delete_reservation')} ${reservation.id}`}
          onClick={handleDeleteClick}
        >
          {t('delete_reservation')}
        </M3TableActionLink>
      )}
    </div>
  );
};

export const Reservations = () => {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const location = useLocation();
  const navState = location.state as ReservationsNavState | null;
  const addToast = useToastStore((s) => s.addToast);
  const role = useAuthStore((s) => s.user?.role);
  const isAdminOrOwner = role === 'ADMIN' || role === 'OWNER';

  const [page, setPage] = useState(0);
  const [reservationToDelete, setReservationToDelete] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [sortField, setSortField] = useState<SortField>(() => navState?.sortField ?? DEFAULT_SORT_FIELD);
  const [sortDir, setSortDir] = useState<SortDir>(() => navState?.sortDir ?? DEFAULT_SORT_DIR);
  const [upcomingOnly, setUpcomingOnly] = useState(() => navState?.upcomingOnly ?? false);

  useEffect(() => {
    const id = setTimeout(() => setDebouncedSearch(searchQuery), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(id);
  }, [searchQuery]);

  // Any filter/sort change invalidates the current page — always restart from page 0.
  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, sortField, sortDir, upcomingOnly]);

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
  }, []);

  const toggleUpcomingOnly = useCallback(() => {
    setUpcomingOnly((prev) => !prev);
  }, []);

  const handlePrevPage = useCallback(() => setPage((p) => p - 1), []);
  const handleNextPage = useCallback(() => setPage((p) => p + 1), []);
  const pageOfLabel = useCallback(
    (current: number, total: number) => t('page_x_of_y', { current, total }),
    [t],
  );

  const sorting = useMemo<SortingState>(
    () => [{ id: sortField, desc: sortDir === 'desc' }],
    [sortField, sortDir],
  );

  const getReservationRowId = useCallback((r: ReservationResponse) => r.id, []);

  const handleSortingChange = useCallback((next: SortingState) => {
    // M3DataTable only ever reports a single active sort (clicking an
    // inactive column selects it ascending; clicking the active one toggles
    // asc/desc) — never an empty array, so there's no "unsorted" case to
    // fall back to a default for.
    setSortField(next[0].id as SortField);
    setSortDir(next[0].desc ? 'desc' : 'asc');
  }, []);

  const searchParams = useMemo(() => ({
    query: debouncedSearch,
    upcomingOnly,
    page,
    size: PAGE_SIZE,
    sort: `${sortField},${sortDir}`,
  }), [debouncedSearch, upcomingOnly, page, sortField, sortDir]);

  const {
    data: reservationsPage,
    isLoading: reservationsLoading,
    error: reservationsError,
    refetch: refetchReservations,
  } = useReservationsSearch(searchParams);
  const { data: roomsData, isLoading: roomsLoading, error: roomsErrorRaw, refetch: refetchRooms } = useRoomsLookup();
  const reservations = reservationsPage?.content ?? EMPTY_RESERVATIONS;
  const totalPages = reservationsPage?.totalPages ?? 1;
  const rooms = roomsData ?? EMPTY_ROOMS;
  const loading = reservationsLoading || roomsLoading;
  const queryError = reservationsError ?? roomsErrorRaw;
  const error = queryError ? getErrorMessage(queryError, t('failed_load_reservations')) : null;

  const handleRetry = useCallback(() => {
    refetchReservations();
    refetchRooms();
  }, [refetchReservations, refetchRooms]);

  const retryConfirmationEmailMutation = useRetryConfirmationEmail();
  const retryingEmail = retryConfirmationEmailMutation.isPending
    ? (retryConfirmationEmailMutation.variables ?? null)
    : null;

  const handleRetryConfirmationEmail = useCallback(async (id: string) => {
    try {
      await retryConfirmationEmailMutation.mutateAsync(id);
      addToast(t('confirmation_email_retry_success'), 'success');
    } catch (err: unknown) {
      const message = getErrorMessage(err, t('confirmation_email_retry_failed'));
      addToast(message, 'error');
    }
  }, [addToast, t, retryConfirmationEmailMutation]);

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

  const columns = useMemo<ColumnDef<ReservationResponse>[]>(() => [
    {
      id: 'guestFullName',
      header: t('guest_name'),
      enableSorting: false,
      cell: ({ row }) => <span className="font-medium">{row.original.guestFullName}</span>,
    },
    {
      id: 'checkInDate',
      accessorKey: 'checkInDate',
      header: t('check_in'),
      cell: ({ row }) => <span className="text-on-surface-variant">{row.original.checkInDate}</span>,
    },
    {
      id: 'checkOutDate',
      accessorKey: 'checkOutDate',
      header: t('check_out'),
      cell: ({ row }) => <span className="text-on-surface-variant">{row.original.checkOutDate}</span>,
    },
    {
      id: 'rooms',
      header: t('nav_rooms'),
      enableSorting: false,
      cell: ({ row }) => <RoomsCell reservation={row.original} rooms={rooms} />,
    },
    {
      id: 'guests',
      header: t('guests'),
      enableSorting: false,
      cell: ({ row }) => <GuestsCountCell reservation={row.original} />,
    },
    {
      id: 'status',
      accessorKey: 'status',
      header: t('status'),
      cell: ({ row }) => (
        <StatusCell
          reservation={row.original}
          onRetryConfirmationEmail={handleRetryConfirmationEmail}
          retryingEmail={retryingEmail}
          t={t}
        />
      ),
    },
    {
      id: 'actions',
      header: () => <span className="sr-only">{t('actions')}</span>,
      enableSorting: false,
      cell: ({ row }) => (
        <ActionsCell
          reservation={row.original}
          onCheckIn={handleCheckIn}
          onView={handleView}
          onEdit={handleEdit}
          onDelete={isAdminOrOwner ? handleDeleteRequest : undefined}
          t={t}
        />
      ),
    },
  ], [t, rooms, handleRetryConfirmationEmail, retryingEmail, handleCheckIn, handleView, handleEdit,
      isAdminOrOwner, handleDeleteRequest]);

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
          <M3TextField
            label={t('search_placeholder')}
            hideLabel
            leadingIcon="search"
            type="search"
            value={searchQuery}
            onChange={handleSearchChange}
            className="w-full sm:w-56"
          />
          <button
            type="button"
            aria-pressed={upcomingOnly}
            onClick={toggleUpcomingOnly}
            className={`px-3 py-1.5 rounded-full text-xs font-medium font-body border transition-colors ${
              upcomingOnly
                ? 'bg-primary text-on-primary border-primary'
                : 'bg-transparent text-on-surface-variant border-outline-variant hover:border-outline'
            }`}
          >
            {t('reservations_upcoming_filter')}
          </button>
          <M3Button data-testid="new-reservation-btn" icon="add" onClick={handleNewReservation}>
            {t('new_reservation')}
          </M3Button>
        </div>
      </div>

      {loading ? (
        <M3LoadingState label={t('loading')} />
      ) : error ? (
        <M3ErrorState
          title={t('error_loading_reservations')}
          message={error}
          retryLabel={t('try_again')}
          onRetry={handleRetry}
        />
      ) : (
        <M3DataTable
          data={reservations}
          columns={columns}
          getRowId={getReservationRowId}
          sorting={sorting}
          onSortingChange={handleSortingChange}
          emptyMessage={upcomingOnly ? t('no_upcoming_reservations_found') : t('no_reservations_found')}
        />
      )}

      {!loading && !error && (
        <M3Pagination
          page={page}
          totalPages={totalPages}
          onPrev={handlePrevPage}
          onNext={handleNextPage}
          pageLabel={t('pagination')}
          prevLabel={t('prev_page')}
          nextLabel={t('next_page')}
          pageOfLabel={pageOfLabel}
        />
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
