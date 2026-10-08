import { useState, useCallback, useMemo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import type { SortingState } from '@tanstack/react-table';
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
import { getErrorMessage, todayIsoDate } from '../utils';
import { reservationService } from '../services';
import {
  useReservationsSearch,
  useRoomsLookup,
  useRetryConfirmationEmail,
} from '../hooks/queries';
import { useDebounce, useListRangeSummary } from '../hooks';
import { useReservationRowActions } from './Reservations/useReservationRowActions';
import { useReservationColumns } from './Reservations/useReservationColumns';
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

  const [page, setPage] = useState(0);
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
  // Adjusted during render (same pattern as Billing) rather than in an effect.
  const activeFilters = { debouncedSearch, sortField, sortDir, preset };
  const [prevFilters, setPrevFilters] = useState(activeFilters);
  if (
    prevFilters.debouncedSearch !== activeFilters.debouncedSearch ||
    prevFilters.sortField !== activeFilters.sortField ||
    prevFilters.sortDir !== activeFilters.sortDir ||
    prevFilters.preset !== activeFilters.preset
  ) {
    setPrevFilters(activeFilters);
    setPage(0);
  }

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

  const {
    reservationToDelete, deleting, handleDeleteRequest, handleDeleteDialogClose, handleDeleteConfirm,
    reservationToMarkNoShow, markingNoShow, handleMarkNoShowRequest, handleMarkNoShowDialogClose,
    handleMarkNoShowConfirm,
  } = useReservationRowActions(reservations);

  const columns = useReservationColumns({
    rooms,
    onRetryConfirmationEmail: handleRetryConfirmationEmail,
    retryingEmail,
    onCheckIn: handleCheckIn,
    onView: handleView,
    onEdit: handleEdit,
    onDelete: isAdminOrOwner ? handleDeleteRequest : undefined,
    onMarkNoShow: handleMarkNoShowRequest,
  });

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
        searchPlaceholder={t('reservations_search_hint')}
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
