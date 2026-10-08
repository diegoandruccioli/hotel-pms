import { useState, useCallback, memo, useMemo } from 'react';
import { format, startOfMonth } from 'date-fns';
import type { SortingState } from '@tanstack/react-table';
import type { InvoiceResponse, InvoiceSearchResult, InvoiceStatus } from '../types';
import { Alert } from '../components/Alert';
import { ListToolbar } from '../components/ListToolbar';
import { PageHeader } from '../components/PageHeader';
import { M3Button } from '../components/m3';
import { M3DataTable } from '../components/m3';
import { M3EmptyState } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { M3FilterChip } from '../components/m3';
import { M3Pagination } from '../components/m3';
import { M3TextField } from '../components/m3';
import { BillingKpiCards } from './Billing/BillingKpiCards';
import { PaymentModal } from './Billing/PaymentModal';
import { InvoiceDetailModal } from './Billing/InvoiceDetailModal';
import { useInvoiceColumns } from './Billing/useInvoiceColumns';
import { useTranslation } from 'react-i18next';
import { useInvoicesSearch, useOwnerFinancialSummary, usePatchInvoiceInCache } from '../hooks/queries';
import { useDebounce, useListRangeSummary } from '../hooks';
import { getErrorMessage } from '../utils';
import { billingService } from '../services';
import { useAuthStore, useToastStore } from '../store';

const PAGE_SIZE = 20;
const DEFAULT_SORT_FIELD = 'issueDate';
const DEFAULT_SORT_DIR: 'asc' | 'desc' = 'desc';

/**
 * Punto 5 (piano pilota) guardrail: corrispettivi telematici Horeca are not
 * implemented (docs-only decision, see backup/DECISIONS.md ADR-006) and the
 * fiscal numbering hasn't been separated into a pilot sezionale yet -- every
 * FATTURA/RICEVUTA generated here still burns a progressive from the same
 * live sequence. Defaults to shown (safer for the actual pilot deployment);
 * set VITE_PILOT_MODE=false to hide once corrispettivi/sezionale are settled
 * with a commercialista.
 */
const PILOT_MODE = import.meta.env.VITE_PILOT_MODE !== 'false';

const EMPTY_RESULTS: InvoiceSearchResult[] = [];

export const Billing = memo(() => {
  const { t } = useTranslation('common');
  const { user } = useAuthStore();
  const addToast = useToastStore((s) => s.addToast);
  const isAdminOrOwner = user?.role === 'ADMIN' || user?.role === 'OWNER';
  const [page, setPage] = useState(0);
  const [paymentTarget, setPaymentTarget] = useState<InvoiceResponse | null>(null);
  const [detailTarget, setDetailTarget]   = useState<InvoiceResponse | null>(null);
  const [statusFilter, setStatusFilter] = useState<InvoiceStatus | 'ALL'>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const debouncedSearch = useDebounce(searchQuery);
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [sortField, setSortField] = useState(DEFAULT_SORT_FIELD);
  const handleExportCsv = useCallback(async () => {
    try {
      await billingService.exportInvoicesCsv({
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        query: debouncedSearch,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
      });
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('csv_export_failed')), 'error');
    }
  }, [statusFilter, debouncedSearch, dateFrom, dateTo, addToast, t]);
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>(DEFAULT_SORT_DIR);

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
  const summary = useListRangeSummary(page, PAGE_SIZE, results.length, data?.totalElements ?? 0);

  // Month-to-date window for the KPI cards; the summary endpoint is owner/admin only.
  const [monthStart, today] = useMemo(() => {
    const now = new Date();
    return [format(startOfMonth(now), 'yyyy-MM-dd'), format(now, 'yyyy-MM-dd')];
  }, []);
  const { data: kpiSummary } = useOwnerFinancialSummary(monthStart, today, isAdminOrOwner);
  const error = queryError ? getErrorMessage(queryError, t('failed_load_invoices')) : null;
  const handleRetry = useCallback(() => { refetch(); }, [refetch]);

  const patchInvoiceInCache = usePatchInvoiceInCache();

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

  const getInvoiceRowId = useCallback((r: InvoiceSearchResult) => r.invoice.id, []);

  const columns = useInvoiceColumns({ onView: handleOpenDetail, onPay: handleOpenPayment });

  return (
    <div className="space-y-6">
      {PILOT_MODE && (
        <Alert tone="warning" className="font-medium">{t('pilot_mode_fiscal_banner')}</Alert>
      )}
      <PageHeader
        icon="receipt_long"
        title={t('nav_billing')}
        subtitle={t('billing_subtitle')}
      />

      {isAdminOrOwner && kpiSummary && <BillingKpiCards summary={kpiSummary} />}

      <ListToolbar
        searchLabel={t('invoice_search_placeholder')}
        searchPlaceholder={t('invoice_search_hint')}
        searchValue={searchQuery}
        onSearchChange={handleSearchChange}
        filtersLabel={t('filter_status')}
        trailing={
          <>
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
            {isAdminOrOwner && (
              <M3Button icon="download" variant="tonal" onClick={handleExportCsv}>
                {t('export_csv')}
              </M3Button>
            )}
          </>
        }
      >
        {(['ALL', 'ISSUED', 'PAID', 'CANCELLED'] as const).map((s) => (
          <M3FilterChip
            key={s}
            value={s}
            selected={statusFilter === s}
            label={s === 'ALL' ? t('filter_all') : t(`invoice_status_${s}`, s)}
            onValueSelect={setStatusFilter}
          />
        ))}
      </ListToolbar>

      {loading ? (
        <M3LoadingState label={t('loading')} />
      ) : error ? (
        <M3ErrorState
          title={t('error_loading_invoices')}
          message={error}
          retryLabel={t('try_again')}
          onRetry={handleRetry}
        />
      ) : results.length === 0 ? (
        <M3EmptyState icon="receipt_long" title={t('no_invoices')} />
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
          summary={summary}
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
