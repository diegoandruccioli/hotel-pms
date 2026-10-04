import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import type { ColumnDef, SortingState } from '@tanstack/react-table';
import type { ReservationResponse, RoomResponse } from '../types';
import { PageHeader } from '../components/PageHeader';
import { ListToolbar } from '../components/ListToolbar';
import { M3Button } from '../components/m3';
import { M3DataTable } from '../components/m3';
import { M3ConfirmDialog } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { M3FilterChip } from '../components/m3';
import { M3Pagination } from '../components/m3';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../store';
import { useToastStore } from '../store';
import { EMPTY_PLACEHOLDER, getErrorMessage, nightsBetween, todayIsoDate } from '../utils';
import { reservationService } from '../services';
import {
  useReservationsSearch,
  useRoomsLookup,
  useDeleteReservation,
  useRetryConfirmationEmail,
  useUpdateReservationStatus,
} from '../hooks/queries';
import { useDebounce, useFormatters, useListRangeSummary } from '../hooks';
import {
  ActionsCell,
  GuestNameCell,
  GuestsCountCell,
  RoomsCell,
  StatusCell,
} from './Reservations/ReservationRowCells';
import { RESERVATION_PRESETS, reservationFilterParams } from './Reservations/reservationFilters';
import type { ReservationPreset } from './Reservations/reservationFilters';

const PAGE_SIZE = 20;
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

const PRESET_LABEL_KEYS: Record<ReservationPreset, string> = {
  all: 'reservations_filter_all',
  upcoming: 'reservations_upcoming_filter',
  pending: 'reservations_filter_pending',
  arrivalsToday: 'reservations_filter_arrivals_today',
  inHouse: 'reservations_filter_in_house',
  cancelled: 'reservations_filter_cancelled',
};

const EMPTY_MESSAGE_KEYS: Partial<Record<ReservationPreset, string>> = {
  all: 'no_reservations_found',
  upcoming: 'no_upcoming_reservations_found',
};

export const Reservations = () => {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const location = useLocation();
  const navState = location.state as ReservationsNavState | null;
  const addToast = useToastStore((s) => s.addToast);
  const role = useAuthStore((s) => s.user?.role);
  const isAdminOrOwner = role === 'ADMIN' || role === 'OWNER';
  const { formatDate } = useFormatters();

  const [page, setPage] = useState(0);
  const [reservationToDelete, setReservationToDelete] = useState<string | null>(null);
  const [reservationToMarkNoShow, setReservationToMarkNoShow] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const debouncedSearch = useDebounce(searchQuery);
  const [sortField, setSortField] = useState<SortField>(() => navState?.sortField ?? DEFAULT_SORT_FIELD);
  const [sortDir, setSortDir] = useState<SortDir>(() => navState?.sortDir ?? DEFAULT_SORT_DIR);
  const [preset, setPreset] = useState<ReservationPreset>(() => (navState?.upcomingOnly ? 'upcoming' : 'all'));
  const handlePresetSelect = useCallback((next: ReservationPreset) => {
    setPage(0);
    setPreset(next);
  }, []);
  const filterParams = useMemo(() => reservationFilterParams(preset, todayIsoDate()), [preset]);

  const handleExportCsv = useCallback(async () => {
    try {
      await reservationService.exportReservationsCsv({ query: searchQuery, upcomingOnly: false, ...filterParams });
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('csv_export_failed')), 'error');
    }
  }, [searchQuery, filterParams, addToast, t]);

  // Any filter/sort change invalidates the current page — always restart from page 0.
  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, sortField, sortDir, preset]);

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
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
    upcomingOnly: false,
    page,
    size: PAGE_SIZE,
    sort: `${sortField},${sortDir}`,
    ...filterParams,
  }), [debouncedSearch, page, sortField, sortDir, filterParams]);

  const {
    data: reservationsPage,
    isLoading: reservationsLoading,
    error: reservationsError,
    refetch: refetchReservations,
  } = useReservationsSearch(searchParams);
  const { data: roomsData, isLoading: roomsLoading, error: roomsErrorRaw, refetch: refetchRooms } = useRoomsLookup();
  const reservations = reservationsPage?.content ?? EMPTY_RESERVATIONS;
  const totalPages = reservationsPage?.totalPages ?? 1;
  const totalElements = reservationsPage?.totalElements ?? 0;
  const rooms = roomsData ?? EMPTY_ROOMS;
  const loading = reservationsLoading || roomsLoading;
  const queryError = reservationsError ?? roomsErrorRaw;
  const error = queryError ? getErrorMessage(queryError, t('failed_load_reservations')) : null;

  const summary = useListRangeSummary(page, PAGE_SIZE, reservations.length, totalElements);

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

  const handleViewGroups = useCallback(() => {
    navigate('/reservations/groups');
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
  const columns = useMemo<ColumnDef<ReservationResponse>[]>(() => [
    {
      id: 'guestFullName',
      header: t('guest_name'),
      enableSorting: false,
      cell: ({ row }) => <GuestNameCell name={row.original.guestFullName} />,
    },
    {
      id: 'checkInDate',
      accessorKey: 'checkInDate',
      header: t('check_in'),
      cell: ({ row }) => <span className="text-on-surface-variant">{formatDate(row.original.checkInDate)}</span>,
    },
    {
      id: 'checkOutDate',
      accessorKey: 'checkOutDate',
      header: t('check_out'),
      cell: ({ row }) => <span className="text-on-surface-variant">{formatDate(row.original.checkOutDate)}</span>,
    },
    {
      id: 'nights',
      header: t('nights'),
      enableSorting: false,
      cell: ({ row }) => (
        <span data-testid={`nights-${row.original.id}`} className="text-on-surface-variant tabular-nums">
          {nightsBetween(row.original.checkInDate, row.original.checkOutDate) ?? EMPTY_PLACEHOLDER}
        </span>
      ),
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
          onMarkNoShow={handleMarkNoShowRequest}
          t={t}
        />
      ),
    },
  ], [t, formatDate, rooms, handleRetryConfirmationEmail, retryingEmail, handleCheckIn, handleView, handleEdit,
      isAdminOrOwner, handleDeleteRequest, handleMarkNoShowRequest]);

  const emptyMessage = EMPTY_MESSAGE_KEYS[preset] ? t(EMPTY_MESSAGE_KEYS[preset]) : t('no_reservations_match_filter');

  return (
    <div className="space-y-6">
      <PageHeader
        icon="event"
        title={t('nav_reservations')}
        subtitle={t('reservations_subtitle')}
        actions={
          <>
            <M3Button
              data-testid="view-groups-btn"
              icon="groups"
              variant="outlined"
              onClick={handleViewGroups}
            >
              {t('nav_reservation_groups')}
            </M3Button>
            <M3Button data-testid="new-reservation-btn" icon="add" onClick={handleNewReservation}>
              {t('new_reservation')}
            </M3Button>
          </>
        }
      />

      <ListToolbar
        searchLabel={t('search_placeholder')}
        filtersLabel={t('reservations_filters_label')}
        searchValue={searchQuery}
        onSearchChange={handleSearchChange}
        trailing={
          <M3Button icon="download" variant="tonal" onClick={handleExportCsv}>
            {t('export_csv')}
          </M3Button>
        }
      >
        {RESERVATION_PRESETS.map((value) => (
          <M3FilterChip
            key={value}
            value={value}
            selected={preset === value}
            onValueSelect={handlePresetSelect}
            label={t(PRESET_LABEL_KEYS[value])}
          />
        ))}
      </ListToolbar>

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
          emptyMessage={emptyMessage}
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
          summary={summary}
        />
      )}
      {reservationToDelete && (
        <M3ConfirmDialog
          title={t('delete_reservation')}
          titleId="confirm-delete-reservation-dialog"
          message={t('delete_reservation_confirm')}
          onConfirm={handleDeleteConfirm}
          onCancel={handleDeleteDialogClose}
          loading={deleting}
        />
      )}
      {reservationToMarkNoShow && (
        <M3ConfirmDialog
          title={t('mark_no_show')}
          titleId="confirm-mark-no-show-dialog"
          message={t('mark_no_show_confirm')}
          onConfirm={handleMarkNoShowConfirm}
          onCancel={handleMarkNoShowDialogClose}
          loading={markingNoShow}
        />
      )}
    </div>
  );
};
