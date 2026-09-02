import { useState, useEffect, useCallback, memo, useMemo } from 'react';
import type { ColumnDef, SortingState } from '@tanstack/react-table';
import type { InvoiceResponse, InvoiceSearchResult, InvoiceStatus } from '../types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3DataTable } from '../components/m3';
import { M3StatusChip } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { M3Pagination } from '../components/m3';
import { M3TextField } from '../components/m3';
import { PaymentModal } from './Billing/PaymentModal';
import { InvoiceDetailModal } from './Billing/InvoiceDetailModal';
import { useTranslation } from 'react-i18next';
import { useInvoicesSearch, usePatchInvoiceInCache } from '../hooks/queries/useInvoices';
import { getErrorMessage } from '../utils';

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;
const DEFAULT_SORT_FIELD = 'issueDate';
const DEFAULT_SORT_DIR: 'asc' | 'desc' = 'desc';

const getStatusTone = (status: InvoiceStatus) => {
  switch (status) {
    case 'ISSUED': return 'warning' as const;
    case 'PAID':   return 'success' as const;
    case 'CANCELLED': return 'error' as const;
    default: return 'neutral' as const;
  }
};

const VIEW_BTN_CLASS = [
  'text-primary hover:text-primary/80 font-medium text-sm mr-4',
  'focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-sm',
].join(' ');

const PAY_BTN_CLASS = [
  'text-tertiary hover:text-tertiary/80 font-medium text-sm',
  'focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-tertiary rounded-sm',
].join(' ');

interface ActionsCellProps {
  invoice: InvoiceResponse;
  onView: (inv: InvoiceResponse) => void;
  onPay: (inv: InvoiceResponse) => void;
  tView: string;
  tRegisterPayment: string;
}

const ActionsCell = ({ invoice, onView, onPay, tView, tRegisterPayment }: ActionsCellProps) => {
  const handleView = useCallback(() => onView(invoice), [onView, invoice]);
  const handlePay  = useCallback(() => onPay(invoice),  [onPay,  invoice]);

  return (
    <div className="text-right">
      <button type="button" onClick={handleView} className={VIEW_BTN_CLASS}>
        {tView}
      </button>
      {invoice.status !== 'PAID' && invoice.status !== 'CANCELLED' && (
        <button type="button" onClick={handlePay} className={PAY_BTN_CLASS}>
          {tRegisterPayment}
        </button>
      )}
    </div>
  );
};

const StatusFilterChip = memo(({ value, active, label, onClick }: {
  value: InvoiceStatus | 'ALL';
  active: boolean;
  label: string;
  onClick: (v: InvoiceStatus | 'ALL') => void;
}) => {
  const handleClick = useCallback(() => onClick(value), [onClick, value]);
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={handleClick}
      className={`px-3 py-1.5 rounded-full text-xs font-medium font-body border transition-colors ${
        active
          ? 'bg-primary text-on-primary border-primary'
          : 'bg-transparent text-on-surface-variant border-outline-variant hover:border-outline'
      }`}
    >
      {label}
    </button>
  );
});
StatusFilterChip.displayName = 'StatusFilterChip';

const EMPTY_RESULTS: InvoiceSearchResult[] = [];

export const Billing = memo(() => {
  const { t, i18n } = useTranslation('common');
  const [page, setPage] = useState(0);
  const [paymentTarget, setPaymentTarget] = useState<InvoiceResponse | null>(null);
  const [detailTarget, setDetailTarget]   = useState<InvoiceResponse | null>(null);
  const [statusFilter, setStatusFilter] = useState<InvoiceStatus | 'ALL'>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [sortField, setSortField] = useState(DEFAULT_SORT_FIELD);
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>(DEFAULT_SORT_DIR);

  useEffect(() => {
    const id = setTimeout(() => setDebouncedSearch(searchQuery), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(id);
  }, [searchQuery]);

  // Any filter change invalidates the current page — always restart from page 0.
  // Adjusted during render (React's documented pattern for this — see
  // https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes)
  // rather than in a useEffect, which would run an extra commit after the
  // filter change instead of resetting in the same render pass.
  const activeFilters = { debouncedSearch, statusFilter, dateFrom, dateTo, sortField, sortDir };
  const [prevFilters, setPrevFilters] = useState(activeFilters);
  if (
    prevFilters.debouncedSearch !== activeFilters.debouncedSearch ||
    prevFilters.statusFilter !== activeFilters.statusFilter ||
    prevFilters.dateFrom !== activeFilters.dateFrom ||
    prevFilters.dateTo !== activeFilters.dateTo ||
    prevFilters.sortField !== activeFilters.sortField ||
    prevFilters.sortDir !== activeFilters.sortDir
  ) {
    setPrevFilters(activeFilters);
    setPage(0);
  }

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
  }, []);

  const handleDateFromChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setDateFrom(e.target.value);
  }, []);

  const handleDateToChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setDateTo(e.target.value);
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

  const handleSortingChange = useCallback((next: SortingState) => {
    setSortField(next[0].id);
    setSortDir(next[0].desc ? 'desc' : 'asc');
  }, []);

  const searchParams = useMemo(() => ({
    status: statusFilter === 'ALL' ? undefined : statusFilter,
    query: debouncedSearch,
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
    page,
    size: PAGE_SIZE,
    sort: `${sortField},${sortDir}`,
  }), [statusFilter, debouncedSearch, dateFrom, dateTo, page, sortField, sortDir]);

  const { data, isLoading: loading, error: queryError, refetch } = useInvoicesSearch(searchParams);
  const results = data?.content ?? EMPTY_RESULTS;
  const totalPages = data?.totalPages ?? 1;
  const error = queryError ? getErrorMessage(queryError, t('failed_load_invoices')) : null;
  const handleRetry = useCallback(() => { refetch(); }, [refetch]);

  const patchInvoiceInCache = usePatchInvoiceInCache();

  const handleStatusFilterClick = useCallback((s: InvoiceStatus | 'ALL') => {
    setStatusFilter(s);
  }, []);

  const handlePaid = useCallback((updated: InvoiceResponse) => {
    patchInvoiceInCache(updated);
  }, [patchInvoiceInCache]);

  const handleOpenDetail  = useCallback((inv: InvoiceResponse) => setDetailTarget(inv), []);
  const handleOpenPayment = useCallback((inv: InvoiceResponse) => setPaymentTarget(inv), []);
  const handleCloseDetail  = useCallback(() => setDetailTarget(null), []);
  const handleClosePayment = useCallback(() => setPaymentTarget(null), []);
  const handleInvoiceUpdated = useCallback((updated: InvoiceResponse) => {
    patchInvoiceInCache(updated);
    setDetailTarget(updated);
  }, [patchInvoiceInCache]);

  const formatCurrency = useCallback(
    (amount: number) =>
      new Intl.NumberFormat(i18n.language, { style: 'currency', currency: 'EUR' }).format(amount),
    [i18n.language],
  );

  const formatDate = useCallback(
    (dateStr?: string) => {
      if (!dateStr) return '—';
      return new Date(dateStr).toLocaleDateString(i18n.language);
    },
    [i18n.language],
  );

  const tView            = t('view');
  const tRegisterPayment = t('register_payment');
  const tPending         = t('pending');

  const getInvoiceRowId = useCallback((r: InvoiceSearchResult) => r.invoice.id, []);

  const columns = useMemo<ColumnDef<InvoiceSearchResult>[]>(() => [
    {
      id: 'invoiceNumber',
      enableSorting: false,
      header: t('invoice_number'),
      cell: ({ row }) => (
        <span className="font-medium">
          {row.original.invoice.invoiceNumber || (
            <span className="text-on-surface-variant italic">{tPending}</span>
          )}
        </span>
      ),
    },
    {
      id: 'guestName',
      enableSorting: false,
      header: t('guest_name'),
      cell: ({ row }) => <span className="text-on-surface-variant">{row.original.guestName ?? '—'}</span>,
    },
    {
      id: 'issueDate',
      accessorFn: (r) => r.invoice.issueDate,
      header: t('issue_date'),
      cell: ({ row }) => (
        <span className="text-on-surface-variant">{formatDate(row.original.invoice.issueDate)}</span>
      ),
    },
    {
      id: 'totalAmount',
      accessorFn: (r) => r.invoice.totalAmount,
      header: t('total_amount'),
      cell: ({ row }) => <span className="font-medium">{formatCurrency(row.original.invoice.totalAmount)}</span>,
    },
    {
      id: 'status',
      accessorFn: (r) => r.invoice.status,
      header: t('status'),
      cell: ({ row }) => (
        <M3StatusChip
          label={t(`invoice_status_${row.original.invoice.status}`, row.original.invoice.status)}
          tone={getStatusTone(row.original.invoice.status)}
        />
      ),
    },
    {
      id: 'actions',
      enableSorting: false,
      header: () => <span className="sr-only">{t('actions')}</span>,
      cell: ({ row }) => (
        <ActionsCell
          invoice={row.original.invoice}
          onView={handleOpenDetail}
          onPay={handleOpenPayment}
          tView={tView}
          tRegisterPayment={tRegisterPayment}
        />
      ),
    },
  ], [t, tPending, formatDate, formatCurrency, handleOpenDetail, handleOpenPayment, tView, tRegisterPayment]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center">
            <MaterialIcon name="receipt_long" className="mr-2 text-primary" />
            {t('nav_billing')}
          </h1>
          <p className="text-sm font-body text-on-surface-variant mt-1">{t('billing_subtitle')}</p>
        </div>
        <M3TextField
          label={t('invoice_search_placeholder')}
          hideLabel
          leadingIcon="search"
          type="search"
          value={searchQuery}
          onChange={handleSearchChange}
          className="w-full sm:w-72"
        />
      </div>

      <div className="flex flex-wrap items-center gap-4">
        <div className="flex flex-wrap gap-2" role="group" aria-label={t('filter_status')}>
          {(['ALL', 'ISSUED', 'PAID', 'CANCELLED'] as const).map((s) => (
            <StatusFilterChip
              key={s}
              value={s}
              active={statusFilter === s}
              label={s === 'ALL' ? t('filter_all') : t(`invoice_status_${s}`, s)}
              onClick={handleStatusFilterClick}
            />
          ))}
        </div>
        <div className="flex items-center gap-2 text-sm font-body">
          <M3TextField
            label={t('date_from')}
            type="date"
            value={dateFrom}
            onChange={handleDateFromChange}
            className="w-40"
          />
          <M3TextField
            label={t('date_to')}
            type="date"
            value={dateTo}
            onChange={handleDateToChange}
            className="w-40"
          />
        </div>
      </div>

      {loading ? (
        <M3LoadingState label={t('loading')} />
      ) : error ? (
        <M3ErrorState
          title={t('error_loading_invoices')}
          message={error}
          retryLabel={t('try_again')}
          onRetry={handleRetry}
        />
      ) : (
        <M3DataTable
          data={results}
          columns={columns}
          getRowId={getInvoiceRowId}
          sorting={sorting}
          onSortingChange={handleSortingChange}
          emptyMessage={t('no_invoices')}
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

      {paymentTarget && (
        <PaymentModal
          invoice={paymentTarget}
          onClose={handleClosePayment}
          onPaid={handlePaid}
        />
      )}

      {detailTarget && (
        <InvoiceDetailModal
          invoice={detailTarget}
          onClose={handleCloseDetail}
          onUpdated={handleInvoiceUpdated}
        />
      )}
    </div>
  );
});

Billing.displayName = 'Billing';
