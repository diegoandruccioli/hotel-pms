import { useFormatters, useListRangeSummary } from '../hooks';
import { useState, useCallback, useMemo } from 'react';
import type { ColumnDef, SortingState } from '@tanstack/react-table';
import type { NightAuditRunResponse } from '../types';
import { MaterialIcon } from '../components/MaterialIcon';
import { PageHeader } from '../components/PageHeader';
import { M3DataTable } from '../components/m3';
import { M3StatusChip } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { M3Pagination } from '../components/m3';
import { M3TableActionLink } from '../components/m3';
import { useTranslation } from 'react-i18next';
import { getErrorMessage, nightAuditStatusTone } from '../utils';
import { useNightAuditHistory } from '../hooks/queries';
import { NightAuditRunWizard } from './NightAudit/NightAuditRunWizard';
import { NightAuditDetailDialog } from './NightAudit/NightAuditDetailDialog';
import { NightAuditLastRunSummary } from './NightAudit/NightAuditLastRunSummary';
import { cashTotal, getStatusLabel } from './NightAudit/nightAuditUtils';

const PAGE_SIZE = 20;

interface ViewDetailCellProps {
  run: NightAuditRunResponse;
  /** Already translated, with the date: "View 15/06/2026". */
  label: string;
  onView: (run: NightAuditRunResponse) => void;
  text: string;
}

const ViewDetailCell = ({ run, label, onView, text }: ViewDetailCellProps) => {
  const handleClick = useCallback(() => {
    onView(run);
  }, [onView, run]);

  return (
    <M3TableActionLink onClick={handleClick} aria-label={label}>
      {text}
    </M3TableActionLink>
  );
};

export const NightAudit = () => {
  const { t } = useTranslation('common');

  const [page, setPage] = useState(0);
  const [detailRun, setDetailRun] = useState<NightAuditRunResponse | null>(null);

  const { data: historyPage, isLoading, isPlaceholderData, error: queryError, refetch } = useNightAuditHistory(page, PAGE_SIZE);
  const runs = historyPage?.content ?? [];
  const totalPages = historyPage?.totalPages ?? 1;
  const error = queryError ? getErrorMessage(queryError, t('night_audit_load_failed')) : null;
  const rangeSummary = useListRangeSummary(page, PAGE_SIZE, runs.length, historyPage?.totalElements ?? 0);
  // While the next page loads the previous rows stay on screen (placeholderData), so
  // anything derived from "this page" must wait for the real data.
  const showPageSummary = !isPlaceholderData;
  // Only the first page is "latest": a later page would surface an older closing.
  const lastCompletedRun = page === 0 && showPageSummary && !error ? runs.find((r) => r.status === 'COMPLETED') : undefined;

  const { formatCurrency, formatDate } = useFormatters();

  const handleViewDetail = useCallback((run: NightAuditRunResponse) => {
    setDetailRun(run);
  }, []);

  const handleDetailClose = useCallback(() => {
    setDetailRun(null);
  }, []);

  const handlePrevPage = useCallback(() => setPage((p) => p - 1), []);
  const handleNextPage = useCallback(() => setPage((p) => p + 1), []);
  const pageOfLabel = useCallback(
    (current: number, total: number) => t('page_x_of_y', { current, total }),
    [t],
  );

  const sorting = useMemo<SortingState>(() => [{ id: 'businessDate', desc: true }], []);
  const handleSortingChange = useCallback(() => {
    // Server-side sort is fixed (newest business date first) — history is a
    // closing log, not a user-reorderable list.
  }, []);
  const getRunRowId = useCallback((run: NightAuditRunResponse) => run.id, []);

  const columns = useMemo<ColumnDef<NightAuditRunResponse>[]>(() => [
    {
      id: 'businessDate',
      accessorKey: 'businessDate',
      header: t('night_audit_business_date'),
      cell: ({ row }) => <span className="font-medium">{formatDate(row.original.businessDate)}</span>,
    },
    {
      id: 'status',
      header: t('status'),
      cell: ({ row }) => (
        <M3StatusChip label={getStatusLabel(row.original.status, t)} tone={nightAuditStatusTone[row.original.status]} />
      ),
    },
    {
      id: 'arrivals',
      header: t('night_audit_arrivals'),
      cell: ({ row }) => <span>{row.original.arrivals ?? '—'}</span>,
    },
    {
      id: 'departures',
      header: t('night_audit_departures'),
      cell: ({ row }) => <span>{row.original.departures ?? '—'}</span>,
    },
    {
      id: 'noShowsMarked',
      header: t('night_audit_no_shows_marked'),
      cell: ({ row }) => <span>{row.original.noShowsMarked ?? '—'}</span>,
    },
    {
      id: 'cashTotal',
      header: t('night_audit_cash_total'),
      cell: ({ row }) => (
        row.original.status === 'COMPLETED' ? (
          <span className="flex items-center gap-1.5">
            {formatCurrency(cashTotal(row.original))}
            {row.original.cashSummaryDegraded && (
              <span title={t('night_audit_cash_degraded')}>
                <MaterialIcon name="warning" size={16} className="text-secondary" />
              </span>
            )}
          </span>
        ) : <span>—</span>
      ),
    },
    {
      id: 'runBy',
      header: t('night_audit_run_by'),
      cell: ({ row }) => <span className="text-on-surface-variant">{row.original.runBy}</span>,
    },
    {
      id: 'actions',
      header: () => <span className="sr-only">{t('actions')}</span>,
      cell: ({ row }) => (
        <ViewDetailCell
          run={row.original}
          label={`${t('view')} ${formatDate(row.original.businessDate)}`}
          text={t('view')}
          onView={handleViewDetail}
        />
      ),
    },
  ], [t, formatCurrency, formatDate, handleViewDetail]);

  return (
    <div className="space-y-6">
      <PageHeader
        icon="fact_check"
        title={t('nav_night_audit')}
        subtitle={t('night_audit_subtitle')}
      />

      <NightAuditRunWizard />

      {lastCompletedRun && <NightAuditLastRunSummary run={lastCompletedRun} />}

      {isLoading ? (
        <M3LoadingState label={t('loading')} />
      ) : error ? (
        <M3ErrorState
          title={t('night_audit_load_failed')}
          message={error}
          retryLabel={t('try_again')}
          onRetry={refetch}
        />
      ) : (
        <M3DataTable
          data={runs}
          columns={columns}
          getRowId={getRunRowId}
          sorting={sorting}
          onSortingChange={handleSortingChange}
          emptyMessage={t('night_audit_no_runs_found')}
        />
      )}

      {!isLoading && !error && (
        <M3Pagination
          page={page}
          totalPages={totalPages}
          onPrev={handlePrevPage}
          onNext={handleNextPage}
          pageLabel={t('pagination')}
          prevLabel={t('prev_page')}
          nextLabel={t('next_page')}
          pageOfLabel={pageOfLabel}
          summary={showPageSummary ? rangeSummary : undefined}
        />
      )}

      {detailRun && <NightAuditDetailDialog run={detailRun} onClose={handleDetailClose} />}
    </div>
  );
};
