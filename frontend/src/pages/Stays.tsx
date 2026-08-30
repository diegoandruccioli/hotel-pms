import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import type { ColumnDef, SortingState } from '@tanstack/react-table';
import type { TFunction } from 'i18next';
import { useAuthStore } from '../store/authStore';
import { useToastStore } from '../store/toastStore';
import type { StayResponse, StayStatus } from '../types/stay.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3DataTable } from '../components/m3/M3DataTable';
import { M3LoadingState } from '../components/m3/M3LoadingState';
import { M3ErrorState } from '../components/m3/M3ErrorState';
import { M3Pagination } from '../components/m3/M3Pagination';
import { M3TextField } from '../components/m3/M3TextField';
import { useTranslation } from 'react-i18next';

import { StayStatusChip } from './Stays/StayStatusChip';
import { M3StatusChip } from '../components/m3/M3StatusChip';
import { M3TableActionLink } from '../components/m3/M3TableActionLink';
import { getStatusTone } from './Stays/stayStatusTone';
import { AlloggiatiReportSection } from './Stays/AlloggiatiReportSection';
import { StayGuestManagerDialog } from './Stays/StayGuestManagerDialog';
import { StayExtensionDialog } from './Stays/StayExtensionDialog';
import { getErrorMessage } from '../utils/errorMessage';
import {
  useStaysList,
  useCheckOutStay,
  useRetryInvoiceCreation,
  useRetryCheckoutEmail,
} from '../hooks/queries/useStays';

type StaySortField = 'actualCheckInTime' | 'expectedCheckOutDate' | 'status';
type SortDir = 'asc' | 'desc';

const GuestCell = ({ stay, onGuestClick }: { stay: StayResponse; onGuestClick: (name: string) => void }) => {
  const handleClick = useCallback(() => {
    onGuestClick(stay.guestDisplayName ?? stay.guestId);
  }, [onGuestClick, stay.guestDisplayName, stay.guestId]);

  return (
    <M3TableActionLink onClick={handleClick} className="truncate block max-w-[120px] text-left" title={stay.guestId}>
      {stay.guestDisplayName ?? `${stay.guestId.substring(0, 8)}…`}
    </M3TableActionLink>
  );
};

interface AlloggiatiCellProps {
  stay: StayResponse;
  onRetryInvoice: (s: StayResponse) => void;
  retryingInvoice: string | null;
  onRetryCheckoutEmail: (s: StayResponse) => void;
  retryingEmail: string | null;
  t: TFunction;
}

const AlloggiatiCell = ({ stay, onRetryInvoice, retryingInvoice, onRetryCheckoutEmail, retryingEmail, t }: AlloggiatiCellProps) => {
  const handleRetryInvoice = useCallback(() => onRetryInvoice(stay), [onRetryInvoice, stay]);
  const handleRetryCheckoutEmail = useCallback(() => onRetryCheckoutEmail(stay), [onRetryCheckoutEmail, stay]);

  return (
    <div className="flex flex-col items-start gap-1">
      <span title={stay.alloggiatiSendFailed ? stay.alloggiatiFailureReason ?? undefined : undefined}>
        <M3StatusChip
          label={
            stay.alloggiatiSent
              ? t('alloggiati_sent')
              : stay.alloggiatiSendFailed
                ? t('alloggiati_failed')
                : t('alloggiati_not_sent')
          }
          tone={stay.alloggiatiSent ? 'success' : stay.alloggiatiSendFailed ? 'error' : 'neutral'}
        />
      </span>
      {stay.invoiceCreationFailed && (
        <span className="inline-flex items-center gap-1" title={stay.invoiceCreationFailureReason ?? undefined}>
          <M3StatusChip label={t('invoice_creation_failed')} tone="error" />
          <button
            type="button"
            onClick={handleRetryInvoice}
            disabled={retryingInvoice === stay.id}
            aria-label={t('retry_invoice_creation')}
            className="flex items-center justify-center w-10 h-10 rounded-shape-full text-error hover:bg-error/[0.12] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error disabled:opacity-50"
          >
            <MaterialIcon name={retryingInvoice === stay.id ? 'progress_activity' : 'refresh'} size={16} />
          </button>
        </span>
      )}
      {stay.checkoutEmailFailed && (
        <span className="inline-flex items-center gap-1" title={stay.checkoutEmailFailureReason ?? undefined}>
          <M3StatusChip label={t('checkout_email_failed')} tone="error" />
          <button
            type="button"
            onClick={handleRetryCheckoutEmail}
            disabled={retryingEmail === stay.id}
            aria-label={t('retry_checkout_email')}
            className="flex items-center justify-center w-10 h-10 rounded-shape-full text-error hover:bg-error/[0.12] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error disabled:opacity-50"
          >
            <MaterialIcon name={retryingEmail === stay.id ? 'progress_activity' : 'refresh'} size={16} />
          </button>
        </span>
      )}
    </div>
  );
};

const GuestsCountCell = ({ stay, onManageGuests }: { stay: StayResponse; onManageGuests: (stayId: string) => void }) => {
  const handleClick = useCallback(() => onManageGuests(stay.id), [onManageGuests, stay.id]);
  const count = stay.guests?.length || 0;

  if (stay.status !== 'CHECKED_IN') {
    return (
      <div className="font-medium flex items-center gap-1.5 text-on-surface">
        <MaterialIcon name="group" size={18} />
        <span>{count}</span>
      </div>
    );
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      className="font-medium flex items-center gap-1.5 text-primary hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded-shape-xs"
    >
      <MaterialIcon name="group" size={18} />
      <span>{count}</span>
    </button>
  );
};

const ActionsCell = ({ stay, onCheckOut, checkingOut, onExtend, t }: {
  stay: StayResponse;
  onCheckOut: (s: StayResponse) => void;
  checkingOut: string | null;
  onExtend: (s: StayResponse) => void;
  t: TFunction;
}) => {
  const handleCheckOut = useCallback(() => onCheckOut(stay), [onCheckOut, stay]);
  const handleExtend = useCallback(() => onExtend(stay), [onExtend, stay]);

  if (stay.status !== 'CHECKED_IN') return null;

  return (
    <div className="flex justify-end gap-2">
      <M3Button
        variant="outlined"
        icon="event_repeat"
        onClick={handleExtend}
        id={`extend-btn-${stay.id}`}
        className="text-xs h-10 px-3"
      >
        {t('action_extend_stay')}
      </M3Button>
      <M3Button
        variant="tonal"
        icon={checkingOut === stay.id ? 'progress_activity' : 'logout'}
        loading={checkingOut === stay.id}
        disabled={checkingOut === stay.id}
        onClick={handleCheckOut}
        id={`checkout-btn-${stay.id}`}
        className="text-xs h-10 px-3"
      >
        {t('action_checkout')}
      </M3Button>
    </div>
  );
};

interface StaysNavState {
  statusFilter?: StayStatus | 'ALL';
  sortField?: StaySortField;
  sortDir?: SortDir;
}

const EMPTY_STAYS: StayResponse[] = [];

export const Stays = memo(() => {
  const { t, i18n } = useTranslation('common');
  const navigate = useNavigate();
  const location = useLocation();
  const navState = location.state as StaysNavState | null;
  const [page, setPage] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<StayStatus | 'ALL'>(() => navState?.statusFilter ?? 'ALL');
  const [sortField, setSortField] = useState<StaySortField>(() => navState?.sortField ?? 'actualCheckInTime');
  const [sortDir, setSortDir] = useState<SortDir>(() => navState?.sortDir ?? 'desc');
  const addToast = useToastStore((s) => s.addToast);
  const role = useAuthStore((s) => s.user?.role);
  const isAdminOrOwner = role === 'ADMIN' || role === 'OWNER';

  useEffect(() => {
    const id = setTimeout(() => setDebouncedSearch(searchQuery), 300);
    return () => clearTimeout(id);
  }, [searchQuery]);

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
  }, []);

  const handleStatusFilterClick = useCallback((s: StayStatus | 'ALL') => {
    setStatusFilter(s);
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
  const handlePrevPage = useCallback(() => setPage((p) => p - 1), []);
  const handleNextPage = useCallback(() => setPage((p) => p + 1), []);
  const pageOfLabel = useCallback(
    (current: number, total: number) => t('page_x_of_y', { current, total }),
    [t],
  );
  
  const formatDate = useCallback((dateStr?: string) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleString(i18n.language);
  }, [i18n.language]);

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
        <span className="text-on-surface-variant">{row.original.expectedCheckOutDate ?? '-'}</span>
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
          tone={getStatusTone(row.original.status)}
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
          t={t}
        />
      ),
    },
  ], [t, formatDate, handleGuestNavigate, handleRetryInvoice, retryingInvoice, handleRetryCheckoutEmail,
      retryingEmail, handleCheckOut, checkingOut, handleManageGuests, handleExtendStay]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center">
            <MaterialIcon name="hotel" className="mr-2 text-primary" />
            {t('nav_stays')}
          </h1>
          <p className="text-sm font-body text-on-surface-variant mt-1">{t('stays_subtitle')}</p>
        </div>
        <div className="flex gap-2">
          <M3Button icon="add" onClick={handleNewCheckIn}>
            {t('new_checkin', 'New Check-in')}
          </M3Button>
          <M3Button icon="person_add" variant="outlined" onClick={handleWalkIn}>
            {t('walkin_title', 'Walk-in')}
          </M3Button>
        </div>
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center gap-3">
        <M3TextField
          label={t('search_placeholder')}
          hideLabel
          leadingIcon="search"
          type="search"
          value={searchQuery}
          onChange={handleSearchChange}
          className="w-full sm:w-56"
        />
        <div className="flex flex-wrap gap-2" role="group" aria-label={t('filter_status')}>
          {(['ALL', 'EXPECTED', 'CHECKED_IN', 'CHECKED_OUT'] as const).map((s) => (
            <StayStatusChip
              key={s}
              value={s}
              active={statusFilter === s}
              label={s === 'ALL' ? t('filter_all') : s === 'EXPECTED' ? t('status_expected') : s === 'CHECKED_IN' ? t('status_checked_in') : t('status_checked_out')}
              onClick={handleStatusFilterClick}
            />
          ))}
        </div>
      </div>

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
        />
      )}

      <AlloggiatiReportSection isAdminOrOwner={isAdminOrOwner} />

      <StayGuestManagerDialog stayId={manageGuestsStayId} onClose={handleCloseGuestManager} />
      <StayExtensionDialog stay={extendingStay} onClose={handleCloseExtension} onExtended={handleStayExtended} />
    </div>
  );
});

Stays.displayName = 'Stays';
