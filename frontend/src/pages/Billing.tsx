import { useState, useEffect, useCallback, memo, useMemo } from 'react';
import type { InvoiceResponse, InvoiceSearchResult, InvoiceStatus } from '../types/billing.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Table, M3TableRow, M3TableCell } from '../components/m3/M3Table';
import { M3StatusChip } from '../components/m3/M3StatusChip';
import { M3LoadingState } from '../components/m3/M3LoadingState';
import { M3ErrorState } from '../components/m3/M3ErrorState';
import { M3TableEmptyRow } from '../components/m3/M3EmptyState';
import { M3Pagination } from '../components/m3/M3Pagination';
import { M3TextField } from '../components/m3/M3TextField';
import { PaymentModal } from './Billing/PaymentModal';
import { InvoiceDetailModal } from './Billing/InvoiceDetailModal';
import { useTranslation } from 'react-i18next';
import { useInvoicesSearch, usePatchInvoiceInCache } from '../hooks/queries/useInvoices';
import { getErrorMessage } from '../utils/errorMessage';

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

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
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded',
].join(' ');

const PAY_BTN_CLASS = [
  'text-tertiary hover:text-tertiary/80 font-medium text-sm',
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-tertiary rounded',
].join(' ');

interface InvoiceRowProps {
  result: InvoiceSearchResult;
  onView: (inv: InvoiceResponse) => void;
  onPay: (inv: InvoiceResponse) => void;
  formatDate: (d?: string) => string;
  formatCurrency: (n: number) => string;
  tView: string;
  tRegisterPayment: string;
  tPending: string;
}

const InvoiceRow = memo(({
  result,
  onView,
  onPay,
  formatDate,
  formatCurrency,
  tView,
  tRegisterPayment,
  tPending,
}: InvoiceRowProps) => {
  const { t } = useTranslation('common');
  const { invoice, guestName } = result;
  const handleView = useCallback(() => onView(invoice), [onView, invoice]);
  const handlePay  = useCallback(() => onPay(invoice),  [onPay,  invoice]);

  return (
    <M3TableRow>
      <M3TableCell className="font-medium">
        {invoice.invoiceNumber || (
          <span className="text-on-surface-variant italic">{tPending}</span>
        )}
      </M3TableCell>
      <M3TableCell className="text-on-surface-variant">{guestName ?? '—'}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{formatDate(invoice.issueDate)}</M3TableCell>
      <M3TableCell className="font-medium">{formatCurrency(invoice.totalAmount)}</M3TableCell>
      <M3TableCell>
        <M3StatusChip label={t(`invoice_status_${invoice.status}`, invoice.status)} tone={getStatusTone(invoice.status)} />
      </M3TableCell>
      <M3TableCell className="text-right">
        <button type="button" onClick={handleView} className={VIEW_BTN_CLASS}>
          {tView}
        </button>
        {invoice.status !== 'PAID' && invoice.status !== 'CANCELLED' && (
          <button type="button" onClick={handlePay} className={PAY_BTN_CLASS}>
            {tRegisterPayment}
          </button>
        )}
      </M3TableCell>
    </M3TableRow>
  );
});
InvoiceRow.displayName = 'InvoiceRow';

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

  useEffect(() => {
    const id = setTimeout(() => setDebouncedSearch(searchQuery), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(id);
  }, [searchQuery]);

  // Any filter change invalidates the current page — always restart from page 0.
  // Adjusted during render (React's documented pattern for this — see
  // https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes)
  // rather than in a useEffect, which would run an extra commit after the
  // filter change instead of resetting in the same render pass.
  const activeFilters = { debouncedSearch, statusFilter, dateFrom, dateTo };
  const [prevFilters, setPrevFilters] = useState(activeFilters);
  if (
    prevFilters.debouncedSearch !== activeFilters.debouncedSearch ||
    prevFilters.statusFilter !== activeFilters.statusFilter ||
    prevFilters.dateFrom !== activeFilters.dateFrom ||
    prevFilters.dateTo !== activeFilters.dateTo
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

  const searchParams = useMemo(() => ({
    status: statusFilter === 'ALL' ? undefined : statusFilter,
    query: debouncedSearch,
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
    page,
    size: PAGE_SIZE,
  }), [statusFilter, debouncedSearch, dateFrom, dateTo, page]);

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

  const tableHeaders = useMemo(
    () => [
      t('invoice_number'),
      t('guest_name'),
      t('issue_date'),
      t('total_amount'),
      t('status'),
      <span key="sr" className="sr-only">{t('actions')}</span>,
    ],
    [t],
  );

  const tView            = t('view');
  const tRegisterPayment = t('register_payment');
  const tPending         = t('pending');

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
        <M3Table headers={tableHeaders}>
          {results.length === 0 ? (
            <M3TableEmptyRow colSpan={tableHeaders.length} message={t('no_invoices')} />
          ) : (
            results.map((result) => (
              <InvoiceRow
                key={result.invoice.id}
                result={result}
                onView={handleOpenDetail}
                onPay={handleOpenPayment}
                formatDate={formatDate}
                formatCurrency={formatCurrency}
                tView={tView}
                tRegisterPayment={tRegisterPayment}
                tPending={tPending}
              />
            ))
          )}
        </M3Table>
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
