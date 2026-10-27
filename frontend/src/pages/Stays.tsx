import { useState, useCallback, useMemo, memo } from 'react';
import { useNavigate, useLocation, useSearchParams } from 'react-router-dom';
import type { ColumnDef, SortingState } from '@tanstack/react-table';
import { useAuthStore } from '../store';
import { useToastStore } from '../store';
import type { StayResponse, StayStatus } from '../types';
import { PageHeader } from '../components/PageHeader';
import { M3Button } from '../components/m3';
import { M3DataTable } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { M3Pagination } from '../components/m3';
import { useTranslation } from 'react-i18next';

import { M3FilterChip } from '../components/m3';
import { M3StatusChip } from '../components/m3';
import { ListToolbar } from '../components/ListToolbar';
import { AlloggiatiReportSection } from './Stays/AlloggiatiReportSection';
import { StayGuestManagerDialog } from './Stays/StayGuestManagerDialog';
import { StayExtensionDialog } from './Stays/StayExtensionDialog';
import { StayRoomChangeDialog } from './Stays/StayRoomChangeDialog';
import { ActionsCell, AlloggiatiCell, GuestCell, GuestsCountCell } from './Stays/StayRowCells';
import { getErrorMessage, stayStatusTone, todayIsoDate } from '../utils';
import {
  useStaysList,
  useCheckOutStay,
  useRetryInvoiceCreation,
  useRetryCheckoutEmail,
} from '../hooks/queries';
import { useDebounce, useFormatters, useListRangeSummary } from '../hooks';
import type { StayListFilters } from '../services/stayService';

type StaySortField = 'actualCheckInTime' | 'expectedCheckOutDate' | 'status';
type SortDir = 'asc' | 'desc';

interface StaysNavState {
  statusFilter?: StayStatus | 'ALL';
  sortField?: StaySortField;
  sortDir?: SortDir;
  departingToday?: boolean;
}

interface StaysFilters {
  statusFilter: StayStatus | 'ALL';
  sortField: StaySortField;
  sortDir: SortDir;
  /** Exact expected check-out day, yyyy-MM-dd. */
  departingOn: string | null;
  alloggiatiFailed: boolean;
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const SORT_FIELDS: readonly StaySortField[] = ['actualCheckInTime', 'expectedCheckOutDate', 'status'];
const STATUS_FILTERS = ['ALL', 'EXPECTED', 'CHECKED_IN', 'CHECKED_OUT'] as const;

/**
 * The URL is the source of truth once any filter has been set (`status` is always written with the
 * others). Until then the dashboard's router state seeds the first render, so a card click lands on the
 * right list and a refresh keeps it.
 */
function readFilters(params: URLSearchParams, nav: StaysNavState | null): StaysFilters {
  if (!params.has('status')) {
    return {
      statusFilter: nav?.statusFilter ?? 'ALL',
      sortField: nav?.sortField ?? 'actualCheckInTime',
      sortDir: nav?.sortDir ?? 'desc',
      departingOn: nav?.departingToday ? todayIsoDate() : null,
      alloggiatiFailed: false,
    };
  }
  const status = params.get('status');
  const sort = params.get('sort');
  const departingOn = params.get('departingOn');
  return {
    statusFilter: STATUS_FILTERS.find((s) => s === status) ?? 'ALL',
    sortField: SORT_FIELDS.find((f) => f === sort) ?? 'actualCheckInTime',
    sortDir: params.get('dir') === 'asc' ? 'asc' : 'desc',
    departingOn: departingOn && ISO_DATE.test(departingOn) ? departingOn : null,
    alloggiatiFailed: params.get('alloggiatiFailed') === 'true',
  };
}

const EMPTY_STAYS: StayResponse[] = [];
const STATUS_FILTER_LABEL_KEYS: Record<(typeof STATUS_FILTERS)[number], string> = {
  ALL: 'filter_all',
  EXPECTED: 'status_expected',
  CHECKED_IN: 'status_checked_in',
  CHECKED_OUT: 'status_checked_out',
};

export const Stays = memo(() => {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const location = useLocation();
  const navState = location.state as StaysNavState | null;
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  const debouncedSearch = useDebounce(searchQuery);
  const { statusFilter, sortField, sortDir, departingOn, alloggiatiFailed } = readFilters(searchParams, navState);
  const addToast = useToastStore((s) => s.addToast);
  const role = useAuthStore((s) => s.user?.role);
  const isAdminOrOwner = role === 'ADMIN' || role === 'OWNER';

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
  }, []);

  const current = useMemo<StaysFilters>(
    () => ({ statusFilter, sortField, sortDir, departingOn, alloggiatiFailed }),
    [statusFilter, sortField, sortDir, departingOn, alloggiatiFailed],
  );

  // Every filter lives in the URL and is applied by the server, so a count on the dashboard matches the list.
  const updateFilters = useCallback((patch: Partial<StaysFilters>) => {
    const next = { ...current, ...patch };
    const params = new URLSearchParams({ status: next.statusFilter, sort: next.sortField, dir: next.sortDir });
    if (next.departingOn) params.set('departingOn', next.departingOn);
    if (next.alloggiatiFailed) params.set('alloggiatiFailed', 'true');
    setSearchParams(params, { replace: true });
    setPage(0);
  }, [current, setSearchParams]);

  const sorting = useMemo<SortingState>(
    () => [{ id: sortField, desc: sortDir === 'desc' }],
    [sortField, sortDir],
  );

  const handleSortingChange = useCallback((next: SortingState) => {
    updateFilters({ sortField: next[0].id as StaySortField, sortDir: next[0].desc ? 'desc' : 'asc' });
  }, [updateFilters]);

  const handleStatusSelect = useCallback((value: StayStatus | 'ALL') => {
    updateFilters({ statusFilter: value });
  }, [updateFilters]);

  const today = todayIsoDate();
  const handleToggleDepartingToday = useCallback(() => {
    updateFilters({ departingOn: departingOn === today ? null : today });
  }, [updateFilters, departingOn, today]);
  const handleToggleAlloggiatiFailed = useCallback(() => {
    updateFilters({ alloggiatiFailed: !alloggiatiFailed });
  }, [updateFilters, alloggiatiFailed]);

  const listFilters = useMemo<StayListFilters>(() => ({
    status: statusFilter === 'ALL' ? undefined : statusFilter,
    sortField,
    sortDir,
    departingOn: departingOn ?? undefined,
    alloggiatiFailed: alloggiatiFailed || undefined,
  }), [statusFilter, sortField, sortDir, departingOn, alloggiatiFailed]);

  const { data: staysPage, isLoading: loading, error: queryError, refetch } = useStaysList(page, listFilters);
  const stays = staysPage?.content ?? EMPTY_STAYS;
  const totalPages = staysPage?.totalPages ?? 1;
  const error = queryError ? getErrorMessage(queryError, t('failed_load_stays')) : null;
  const handleRetry = useCallback(() => { refetch(); }, [refetch]);

  // The backend has no free-text filter, so the search box narrows only the loaded page.
  const filteredStays = useMemo(() => {
    if (!debouncedSearch.trim()) return stays;
    const q = debouncedSearch.toLowerCase();
    return stays.filter(
      (s) =>
        s.roomNumber?.toLowerCase().includes(q) ||
        s.guestDisplayName?.toLowerCase().includes(q),
    );
  }, [stays, debouncedSearch]);

  // Search narrows only the loaded page, so a server-wide range would mislead while it is on.
  const filtersActive = debouncedSearch.trim() !== '';
  // Position comes from the response, not local state: the previous page's rows stay visible while the next one loads.
  const summary = useListRangeSummary(
    staysPage?.number ?? 0,
    staysPage?.size ?? stays.length,
    filtersActive ? 0 : stays.length,
    staysPage?.totalElements ?? 0,
  );

  const checkOutMutation = useCheckOutStay();
  const checkingOut = checkOutMutation.isPending ? (checkOutMutation.variables ?? null) : null;
  const handleCheckOut = useCallback(async (stay: StayResponse) => {
    try {
      await checkOutMutation.mutateAsync(stay.id);
      addToast(t('guest_checked_out_success'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('checkout_failed')), 'error');
    }
  }, [addToast, t, checkOutMutation]);

  const retryInvoiceMutation = useRetryInvoiceCreation();
  const retryingInvoice = retryInvoiceMutation.isPending ? (retryInvoiceMutation.variables ?? null) : null;
  const handleRetryInvoice = useCallback(async (stay: StayResponse) => {
    try {
      await retryInvoiceMutation.mutateAsync(stay.id);
      addToast(t('invoice_retry_success'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('invoice_retry_failed')), 'error');
    }
  }, [addToast, t, retryInvoiceMutation]);

  const retryCheckoutEmailMutation = useRetryCheckoutEmail();
  const retryingEmail = retryCheckoutEmailMutation.isPending ? (retryCheckoutEmailMutation.variables ?? null) : null;
  const handleRetryCheckoutEmail = useCallback(async (stay: StayResponse) => {
    try {
      await retryCheckoutEmailMutation.mutateAsync(stay.id);
      addToast(t('checkout_email_retry_success'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('checkout_email_retry_failed')), 'error');
    }
  }, [addToast, t, retryCheckoutEmailMutation]);

  const handleNewCheckIn = useCallback(() => navigate('/reservations', {
    state: { preset: 'arrivalsToday', sortField: 'checkInDate', sortDir: 'asc' },
  }), [navigate]);
  const handleWalkIn = useCallback(() => navigate('/stays/walk-in'), [navigate]);
  const handleGuestNavigate = useCallback((guestId: string) => {
    navigate('/guests', { state: { guestId } });
  }, [navigate]);
  const handleOpenReservation = useCallback((id: string) => {
    navigate(`/reservations/${id}`);
  }, [navigate]);
  const handleOpenDetail = useCallback((stayId: string) => {
    navigate(`/stays/${stayId}`);
  }, [navigate]);

  const [manageGuestsStayId, setManageGuestsStayId] = useState<string | null>(null);
  const handleManageGuests = useCallback((stayId: string) => setManageGuestsStayId(stayId), []);
  const handleCloseGuestManager = useCallback(() => setManageGuestsStayId(null), []);

  const [extendingStay, setExtendingStay] = useState<StayResponse | null>(null);
  const handleExtendStay = useCallback((stay: StayResponse) => setExtendingStay(stay), []);
  const handleCloseExtension = useCallback(() => setExtendingStay(null), []);
  const handleStayExtended = useCallback(() => { refetch(); }, [refetch]);

  const [changingRoomStay, setChangingRoomStay] = useState<StayResponse | null>(null);
  const handleChangeRoom = useCallback((stay: StayResponse) => setChangingRoomStay(stay), []);
  const handleCloseRoomChange = useCallback(() => setChangingRoomStay(null), []);
  const handleRoomChanged = useCallback(() => { refetch(); }, [refetch]);
  const handlePrevPage = useCallback(() => setPage((p) => p - 1), []);
  const handleNextPage = useCallback(() => setPage((p) => p + 1), []);
  const pageOfLabel = useCallback(
    (current: number, total: number) => t('page_x_of_y', { current, total }),
    [t],
  );
  
  const { formatDate } = useFormatters();

  const getStayRowId = useCallback((s: StayResponse) => s.id, []);

  const columns = useMemo<ColumnDef<StayResponse>[]>(() => [
    {
      id: 'roomId',
      header: t('room_id'),
      enableSorting: false,
      cell: ({ row }) => (
        <span className="truncate block max-w-[120px] font-medium" title={row.original.roomId}>
          {row.original.roomNumber ?? `${row.original.roomId.substring(0, 8)}…`}
        </span>
      ),
    },
    {
      id: 'guestId',
      header: t('guest_id'),
      enableSorting: false,
      cell: ({ row }) => <GuestCell stay={row.original} onGuestClick={handleGuestNavigate} />,
    },
    {
      id: 'actualCheckInTime',
      accessorKey: 'actualCheckInTime',
      header: t('check_in'),
      cell: ({ row }) => (
        <span className="text-on-surface-variant">{formatDate(row.original.actualCheckInTime)}</span>
      ),
    },
    {
      id: 'actualCheckOutTime',
      header: t('check_out'),
      enableSorting: false,
      cell: ({ row }) => (
        <span className="text-on-surface-variant">{formatDate(row.original.actualCheckOutTime)}</span>
      ),
    },
    {
      id: 'expectedCheckOutDate',
      accessorKey: 'expectedCheckOutDate',
      header: t('expected_checkout_col'),
      cell: ({ row }) => (
        <span className="text-on-surface-variant">{formatDate(row.original.expectedCheckOutDate)}</span>
      ),
    },
    {
      id: 'guests',
      header: t('guests'),
      enableSorting: false,
      cell: ({ row }) => <GuestsCountCell stay={row.original} onManageGuests={handleManageGuests} />,
    },
    {
      id: 'status',
      accessorKey: 'status',
      header: t('status'),
      cell: ({ row }) => (
        <M3StatusChip
          label={t(`status_${row.original.status.toLowerCase()}`, row.original.status.replace('_', ' '))}
          tone={stayStatusTone[row.original.status]}
        />
      ),
    },
    {
      id: 'alloggiati',
      header: t('alloggiati_column'),
      enableSorting: false,
      cell: ({ row }) => (
        <AlloggiatiCell
          stay={row.original}
          onRetryInvoice={handleRetryInvoice}
          retryingInvoice={retryingInvoice}
          onRetryCheckoutEmail={handleRetryCheckoutEmail}
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
          stay={row.original}
          onCheckOut={handleCheckOut}
          checkingOut={checkingOut}
          onExtend={handleExtendStay}
          onChangeRoom={handleChangeRoom}
          onOpenReservation={handleOpenReservation}
          onOpenDetail={handleOpenDetail}
          t={t}
        />
      ),
    },
  ], [t, formatDate, handleGuestNavigate, handleRetryInvoice, retryingInvoice, handleRetryCheckoutEmail,
      retryingEmail, handleCheckOut, checkingOut, handleManageGuests, handleExtendStay, handleChangeRoom,
      handleOpenReservation, handleOpenDetail]);

  return (
    <div className="space-y-6">
      <PageHeader
        icon="hotel"
        title={t('nav_stays')}
        subtitle={t('stays_subtitle')}
        actions={
          <>
            <M3Button icon="add" onClick={handleNewCheckIn}>
              {t('new_checkin', 'New Check-in')}
            </M3Button>
            <M3Button icon="person_add" variant="outlined" onClick={handleWalkIn}>
              {t('walkin_title', 'Walk-in')}
            </M3Button>
          </>
        }
      />

      <ListToolbar
        searchLabel={t('search_placeholder')}
        searchPlaceholder={t('stays_search_hint')}
        filtersLabel={t('filter_status')}
        searchValue={searchQuery}
        onSearchChange={handleSearchChange}
      >
        {STATUS_FILTERS.map((s) => (
          <M3FilterChip
            key={s}
            value={s}
            selected={statusFilter === s}
            label={t(STATUS_FILTER_LABEL_KEYS[s])}
            onValueSelect={handleStatusSelect}
          />
        ))}
        <M3FilterChip
          value="departingToday"
          selected={departingOn === today}
          label={t('stays_filter_departing_today')}
          onValueSelect={handleToggleDepartingToday}
        />
        <M3FilterChip
          value="alloggiatiFailed"
          selected={alloggiatiFailed}
          label={t('stays_filter_alloggiati_failed')}
          onValueSelect={handleToggleAlloggiatiFailed}
        />
      </ListToolbar>


      {loading ? (
        <M3LoadingState label={t('loading')} />
      ) : error ? (
        <M3ErrorState
          title={t('error_loading_stays')}
          message={error}
          retryLabel={t('try_again')}
          onRetry={handleRetry}
        />
      ) : (
        <M3DataTable
          data={filteredStays}
          columns={columns}
          getRowId={getStayRowId}
          sorting={sorting}
          onSortingChange={handleSortingChange}
          emptyMessage={t('no_active_stays')}
        />
      )}

      {/* Pagination */}
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

      <AlloggiatiReportSection isAdminOrOwner={isAdminOrOwner} />

      <StayGuestManagerDialog stayId={manageGuestsStayId} onClose={handleCloseGuestManager} />
      <StayExtensionDialog stay={extendingStay} onClose={handleCloseExtension} onExtended={handleStayExtended} />
      <StayRoomChangeDialog stay={changingRoomStay} onClose={handleCloseRoomChange} onChanged={handleRoomChanged} />
    </div>
  );
});

Stays.displayName = 'Stays';
