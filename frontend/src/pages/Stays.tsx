import { useState, useCallback, useMemo, memo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
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
import { getErrorMessage, stayStatusTone } from '../utils';
import {
  useStaysList,
  useCheckOutStay,
  useRetryInvoiceCreation,
  useRetryCheckoutEmail,
} from '../hooks/queries';
import { useDebounce, useFormatters, useListRangeSummary } from '../hooks';

type StaySortField = 'actualCheckInTime' | 'expectedCheckOutDate' | 'status';
type SortDir = 'asc' | 'desc';

interface StaysNavState {
  statusFilter?: StayStatus | 'ALL';
  sortField?: StaySortField;
  sortDir?: SortDir;
}

const EMPTY_STAYS: StayResponse[] = [];
const STATUS_FILTERS = ['ALL', 'EXPECTED', 'CHECKED_IN', 'CHECKED_OUT'] as const;
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
  const [page, setPage] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  const debouncedSearch = useDebounce(searchQuery);
  const [statusFilter, setStatusFilter] = useState<StayStatus | 'ALL'>(() => navState?.statusFilter ?? 'ALL');
  const [sortField, setSortField] = useState<StaySortField>(() => navState?.sortField ?? 'actualCheckInTime');
  const [sortDir, setSortDir] = useState<SortDir>(() => navState?.sortDir ?? 'desc');
  const addToast = useToastStore((s) => s.addToast);
  const role = useAuthStore((s) => s.user?.role);
  const isAdminOrOwner = role === 'ADMIN' || role === 'OWNER';

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
  }, []);

  const sorting = useMemo<SortingState>(
    () => [{ id: sortField, desc: sortDir === 'desc' }],
    [sortField, sortDir],
  );

  const handleSortingChange = useCallback((next: SortingState) => {
    setSortField(next[0].id as StaySortField);
    setSortDir(next[0].desc ? 'desc' : 'asc');
  }, []);

  const { data: staysPage, isLoading: loading, error: queryError, refetch } = useStaysList(page);
  const stays = staysPage?.content ?? EMPTY_STAYS;
  const totalPages = staysPage?.totalPages ?? 1;
  const error = queryError ? getErrorMessage(queryError, t('failed_load_stays')) : null;
  const handleRetry = useCallback(() => { refetch(); }, [refetch]);

  const filteredStays = useMemo(() => {
    let result = stays;
    if (statusFilter !== 'ALL') {
      result = result.filter((s) => s.status === statusFilter);
    }
    if (debouncedSearch.trim()) {
      const q = debouncedSearch.toLowerCase();
      result = result.filter(
        (s) =>
          s.roomNumber?.toLowerCase().includes(q) ||
          s.guestDisplayName?.toLowerCase().includes(q),
      );
    }
    const sorted = [...result].sort((a, b) => {
      const cmp = (a[sortField] ?? '').localeCompare(b[sortField] ?? '');
      return sortDir === 'asc' ? cmp : -cmp;
    });
    return sorted;
  }, [stays, statusFilter, debouncedSearch, sortField, sortDir]);

  // Search and status chips narrow only the loaded page, so a server-wide range would mislead while they are on.
  const filtersActive = statusFilter !== 'ALL' || debouncedSearch.trim() !== '';
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

  const handleNewCheckIn = useCallback(() => navigate('/reservations'), [navigate]);
  const handleWalkIn = useCallback(() => navigate('/stays/walk-in'), [navigate]);
  const handleGuestNavigate = useCallback((guestDisplayName: string) => {
    navigate('/guests?search=' + encodeURIComponent(guestDisplayName));
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
          t={t}
        />
      ),
    },
  ], [t, formatDate, handleGuestNavigate, handleRetryInvoice, retryingInvoice, handleRetryCheckoutEmail,
      retryingEmail, handleCheckOut, checkingOut, handleManageGuests, handleExtendStay, handleChangeRoom]);

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
            onValueSelect={setStatusFilter}
          />
        ))}
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
