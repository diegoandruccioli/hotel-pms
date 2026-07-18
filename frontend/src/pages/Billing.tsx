import { useState, useEffect, useCallback, memo, useMemo } from 'react';
import type { InvoiceResponse, InvoiceSearchResult, InvoiceStatus } from '../types/billing.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Table, M3TableRow, M3TableCell } from '../components/m3/M3Table';
import { M3StatusChip } from '../components/m3/M3StatusChip';
import { M3Button } from '../components/m3/M3Button';
import { billingService } from '../services/billingService';
import { PaymentModal } from './Billing/PaymentModal';
import { InvoiceDetailModal } from './Billing/InvoiceDetailModal';
import { useTranslation } from 'react-i18next';

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

export const Billing = memo(() => {
  const { t, i18n } = useTranslation('common');
  const [results, setResults] = useState<InvoiceSearchResult[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
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
  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, statusFilter, dateFrom, dateTo]);

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

  const loadInvoices = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await billingService.searchInvoices({
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        query: debouncedSearch,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
        page,
        size: PAGE_SIZE,
      });
      setResults(data.content);
      setTotalPages(data.totalPages);
    } catch (err: unknown) {
      const e = err as { response?: { data?: { detail?: string } }; message?: string };
      setError(e.response?.data?.detail ?? e.message ?? t('failed_load_invoices'));
    } finally {
      setLoading(false);
    }
  }, [t, statusFilter, debouncedSearch, dateFrom, dateTo, page]);

  useEffect(() => {
    loadInvoices();
  }, [loadInvoices]);

  const handleStatusFilterClick = useCallback((s: InvoiceStatus | 'ALL') => {
    setStatusFilter(s);
  }, []);

  const handlePaid = useCallback((updated: InvoiceResponse) => {
    setResults((prev) => prev.map((r) => (r.invoice.id === updated.id ? { ...r, invoice: updated } : r)));
  }, []);

  const handleOpenDetail  = useCallback((inv: InvoiceResponse) => setDetailTarget(inv), []);
  const handleOpenPayment = useCallback((inv: InvoiceResponse) => setPaymentTarget(inv), []);
  const handleCloseDetail  = useCallback(() => setDetailTarget(null), []);
  const handleClosePayment = useCallback(() => setPaymentTarget(null), []);
  const handleInvoiceUpdated = useCallback((updated: InvoiceResponse) => {
    setResults((prev) => prev.map((r) => (r.invoice.id === updated.id ? { ...r, invoice: updated } : r)));
    setDetailTarget(updated);
  }, []);

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
        <div className="relative">
          <MaterialIcon name="search" size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant pointer-events-none" />
          <input
            type="search"
            value={searchQuery}
            onChange={handleSearchChange}
            placeholder={t('invoice_search_placeholder')}
            aria-label={t('invoice_search_placeholder')}
            className="pl-9 pr-3 py-2 w-full sm:w-72 rounded-shape-xs border border-outline bg-transparent text-sm font-body text-on-surface placeholder:text-on-surface-variant focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none"
          />
        </div>
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
          <label htmlFor="billing-date-from" className="text-on-surface-variant">{t('date_from')}</label>
          <input
            id="billing-date-from"
            type="date"
            value={dateFrom}
            onChange={handleDateFromChange}
            className="px-2 py-1 rounded-shape-xs border border-outline bg-transparent text-on-surface focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none"
          />
          <label htmlFor="billing-date-to" className="text-on-surface-variant">{t('date_to')}</label>
          <input
            id="billing-date-to"
            type="date"
            value={dateTo}
            onChange={handleDateToChange}
            className="px-2 py-1 rounded-shape-xs border border-outline bg-transparent text-on-surface focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none"
          />
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
            <h3 className="text-sm font-medium font-body">{t('error_loading_invoices')}</h3>
            <p className="mt-1 text-sm font-body opacity-80">{error}</p>
            <button
              type="button"
              onClick={loadInvoices}
              className="mt-2 text-sm font-medium underline hover:no-underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-on-error-container rounded"
            >
              {t('try_again')}
            </button>
          </div>
        </div>
      ) : (
        <M3Table headers={tableHeaders}>
          {results.length === 0 ? (
            <tr>
              <td colSpan={6} className="py-8 text-center text-sm font-body text-on-surface-variant">
                {t('no_invoices')}
              </td>
            </tr>
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

      {!loading && !error && totalPages > 1 && (
        <nav aria-label={t('pagination')} className="flex items-center justify-center gap-3">
          <M3Button
            variant="outlined"
            icon="chevron_left"
            disabled={page === 0}
            onClick={handlePrevPage}
            aria-label={t('prev_page')}
          >
            {t('prev_page')}
          </M3Button>
          <span className="text-sm font-body text-on-surface-variant">
            {t('page_x_of_y', { current: page + 1, total: totalPages })}
          </span>
          <M3Button
            variant="outlined"
            icon="chevron_right"
            disabled={page >= totalPages - 1}
            onClick={handleNextPage}
            aria-label={t('next_page')}
          >
            {t('next_page')}
          </M3Button>
        </nav>
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
