import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { useToastStore } from '../store/toastStore';
import type { StayResponse, StayStatus } from '../types/stay.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3Table } from '../components/m3/M3Table';
import { M3LoadingState } from '../components/m3/M3LoadingState';
import { M3ErrorState } from '../components/m3/M3ErrorState';
import { M3TableEmptyRow } from '../components/m3/M3EmptyState';
import { M3Pagination } from '../components/m3/M3Pagination';
import { M3Select } from '../components/m3/M3Select';
import { M3TextField } from '../components/m3/M3TextField';
import { useTranslation } from 'react-i18next';

import { StayRow } from './Stays/StayRow';
import { StayStatusChip } from './Stays/StayStatusChip';
import { getStatusTone } from './Stays/stayStatusTone';
import { AlloggiatiReportSection } from './Stays/AlloggiatiReportSection';
import { getErrorMessage } from '../utils/errorMessage';
import {
  useStaysList,
  useCheckOutStay,
  useRetryInvoiceCreation,
  useRetryCheckoutEmail,
} from '../hooks/queries/useStays';

type StaySortField = 'actualCheckInTime' | 'expectedCheckOutDate' | 'status';
type SortDir = 'asc' | 'desc';

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

  const handleSortFieldChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    setSortField(e.target.value as StaySortField);
  }, []);

  const toggleSortDir = useCallback(() => {
    setSortDir((prev) => (prev === 'asc' ? 'desc' : 'asc'));
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
  const handlePrevPage = useCallback(() => setPage((p) => p - 1), []);
  const handleNextPage = useCallback(() => setPage((p) => p + 1), []);
  const pageOfLabel = useCallback(
    (current: number, total: number) => t('page_x_of_y', { current, total }),
    [t],
  );
  
  const sortOptions = useMemo(() => [
    { value: 'actualCheckInTime', label: t('check_in') },
    { value: 'expectedCheckOutDate', label: t('expected_checkout_col') },
    { value: 'status', label: t('status') },
  ], [t]);

  const formatDate = useCallback((dateStr?: string) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleString(i18n.language);
  }, [i18n.language]);

  const headers = useMemo(() => [
    t('room_id'),
    t('guest_id'),
    t('check_in'),
    t('check_out'),
    t('expected_checkout_col'),
    t('guests'),
    t('status'),
    t('alloggiati_column'),
    <span key="sr" className="sr-only">{t('actions')}</span>
  ], [t]);

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
        <div className="flex items-center gap-2">
          <M3Select
            label={t('sort_by')}
            hideLabel
            options={sortOptions}
            value={sortField}
            onChange={handleSortFieldChange}
          />
          <button
            type="button"
            onClick={toggleSortDir}
            aria-label={sortDir === 'asc' ? t('sort_dir_asc') : t('sort_dir_desc')}
            className="flex items-center justify-center w-10 h-10 rounded-shape-full border border-outline text-on-surface-variant hover:bg-primary/[0.08] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 transition-colors"
          >
            <MaterialIcon name={sortDir === 'asc' ? 'arrow_upward' : 'arrow_downward'} size={20} />
          </button>
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
        <M3Table headers={headers}>
          {filteredStays.length === 0 ? (
            <M3TableEmptyRow colSpan={headers.length} message={t('no_active_stays')} />
          ) : (
            filteredStays.map((stay) => (
              <StayRow
                key={stay.id}
                stay={stay}
                onCheckOut={handleCheckOut}
                checkingOut={checkingOut}
                onRetryInvoice={handleRetryInvoice}
                retryingInvoice={retryingInvoice}
                onRetryCheckoutEmail={handleRetryCheckoutEmail}
                retryingEmail={retryingEmail}
                formatDate={formatDate}
                getStatusTone={getStatusTone}
                t={t}
                onGuestClick={handleGuestNavigate}
              />
            ))
          )}
        </M3Table>
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
    </div>
  );
});

Stays.displayName = 'Stays';
