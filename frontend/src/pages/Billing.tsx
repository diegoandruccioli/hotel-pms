import { useState, useCallback, memo, useMemo, useEffect } from 'react';
import { format, startOfMonth } from 'date-fns';
import { useLocation, useSearchParams } from 'react-router-dom';
import type { SortingState } from '@tanstack/react-table';
import type { InvoiceResponse, InvoiceSearchResult } from '../types';
import { Alert } from '../components/Alert';
import { ListToolbar } from '../components/ListToolbar';
import { ExportCsvButton } from '../components/ExportCsvButton';
import { PageHeader } from '../components/PageHeader';
import { M3DataTable } from '../components/m3';
import { M3EmptyState } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { M3FilterChip } from '../components/m3';
import { M3Button } from '../components/m3';
import { M3Pagination } from '../components/m3';
import { M3TextField } from '../components/m3';
import { BillingKpiCards } from './Billing/BillingKpiCards';
import { PaymentModal } from './Billing/PaymentModal';
import { InvoiceDetailModal } from './Billing/InvoiceDetailModal';
import { useInvoiceColumns } from './Billing/useInvoiceColumns';
import {
  BILLING_STATUS_FILTERS, billingListParamsToSearch, readBillingListParams,
} from './Billing/billingListParams';
import type {
  BillingListParams, BillingNavState, BillingSortField, BillingStatusFilter,
} from './Billing/billingListParams';
import { useTranslation } from 'react-i18next';
import { useInvoicesSearch, useOwnerFinancialSummary, usePatchInvoiceInCache } from '../hooks/queries';
import { useDebounce, useListRangeSummary } from '../hooks';
import { getErrorMessage } from '../utils';
import { billingService } from '../services';
import { useAuthStore, useToastStore } from '../store';

const PAGE_SIZE = 20;

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
  const [paymentTarget, setPaymentTarget] = useState<InvoiceResponse | null>(null);
  const [detailTarget, setDetailTarget]   = useState<InvoiceResponse | null>(null);
  const location = useLocation();
  const navState = location.state as (BillingNavState & { invoiceId?: string }) | null;
  const [searchQuery, setSearchQuery] = useState('');
  const debouncedSearch = useDebounce(searchQuery);
  // Status, dates, sort and page live in the URL (back, refresh and shared links keep them); the
  // search text does not, because it holds guest names and emails.
  const [urlParams, setUrlParams] = useSearchParams();
  const { status: statusFilter, sortField, sortDir, page, dateFrom, dateTo } = readBillingListParams(urlParams, navState);
  const currentList = useMemo<BillingListParams>(
    () => ({ status: statusFilter, sortField, sortDir, page, dateFrom, dateTo }),
    [statusFilter, sortField, sortDir, page, dateFrom, dateTo],
  );
  // Any change other than the page itself invalidates the current page: restart from the first.
  const updateList = useCallback((patch: Partial<BillingListParams>) => {
    setUrlParams(billingListParamsToSearch({ ...currentList, page: 0, ...patch }), { replace: true });
  }, [currentList, setUrlParams]);

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

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
    if (currentList.page > 0) updateList({});
  }, [currentList.page, updateList]);

  const handleStatusSelect = useCallback((next: BillingStatusFilter) => updateList({ status: next }), [updateList]);
  const handleDateFromChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    updateList({ dateFrom: e.target.value });
  }, [updateList]);
  const handleDateToChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    updateList({ dateTo: e.target.value });
  }, [updateList]);

  const handlePrevPage = useCallback(() => updateList({ page: page - 1 }), [updateList, page]);
  const handleNextPage = useCallback(() => updateList({ page: page + 1 }), [updateList, page]);
  const pageOfLabel = useCallback(
    (current: number, total: number) => t('page_x_of_y', { current, total }),
    [t],
  );

  const sorting = useMemo<SortingState>(
    () => [{ id: sortField, desc: sortDir === 'desc' }],
    [sortField, sortDir],
  );

  const handleSortingChange = useCallback((next: SortingState) => {
    updateList({ sortField: next[0].id as BillingSortField, sortDir: next[0].desc ? 'desc' : 'asc' });
  }, [updateList]);

  const hasActiveFilters = statusFilter !== 'ALL' || dateFrom !== '' || dateTo !== '' || debouncedSearch !== '';
  const handleShowAll = useCallback(() => {
    setSearchQuery('');
    setUrlParams(billingListParamsToSearch({
      status: 'ALL', sortField, sortDir, page: 0, dateFrom: '', dateTo: '',
    }), { replace: true });
  }, [setUrlParams, sortField, sortDir]);

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

  // Deep link (e.g. a group's master folio): open that invoice's detail on arrival.
  const linkedInvoiceId = navState?.invoiceId;
  useEffect(() => {
    if (!linkedInvoiceId) return undefined;
    let cancelled = false;
    billingService.getInvoiceById(linkedInvoiceId)
      .then((invoice) => { if (!cancelled) setDetailTarget(invoice); })
      .catch((err: unknown) => {
        if (!cancelled) addToast(getErrorMessage(err, t('failed_load_invoices')), 'error');
      });
    return () => { cancelled = true; };
  }, [linkedInvoiceId, addToast, t]);

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
              <ExportCsvButton onExport={handleExportCsv} />
            )}
          </>
        }
      >
        {BILLING_STATUS_FILTERS.map((s) => (
          <M3FilterChip
            key={s}
            value={s}
            selected={statusFilter === s}
            label={s === 'ALL' ? t('filter_all') : t(`invoice_status_${s}`, s)}
            onValueSelect={handleStatusSelect}
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
        hasActiveFilters ? (
        <M3EmptyState
          icon="receipt_long"
          title={t('no_invoices_filtered')}
          action={
            <M3Button data-testid="invoices-show-all-btn" variant="outlined" onClick={handleShowAll}>
              {t('invoices_show_all')}
            </M3Button>
          }
        />
      ) : (
        <M3EmptyState icon="receipt_long" title={t('no_invoices')} />
      )
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
