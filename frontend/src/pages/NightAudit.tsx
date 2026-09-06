import { useState, useCallback, useMemo } from 'react';
import type { ColumnDef, SortingState } from '@tanstack/react-table';
import type { NightAuditRunResponse } from '../types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3';
import { M3DataTable } from '../components/m3';
import { M3StatusChip } from '../components/m3';
import { M3Dialog } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { M3Pagination } from '../components/m3';
import { M3TextField } from '../components/m3';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useToastStore } from '../store';
import { getErrorMessage } from '../utils';
import { useNightAuditHistory, useRunNightAudit } from '../hooks/queries';

const PAGE_SIZE = 20;

const getStatusTone = (status: NightAuditRunResponse['status']) => {
  switch (status) {
    case 'COMPLETED': return 'success' as const;
    case 'FAILED': return 'error' as const;
    default: return 'neutral' as const;
  }
};

const getStatusLabel = (status: NightAuditRunResponse['status'], t: TFunction) =>
  t(`night_audit_status_${status.toLowerCase()}`);

const cashTotal = (run: NightAuditRunResponse): number =>
  run.cashByMethod.reduce((sum, line) => sum + line.total, 0);

const todayIsoDate = (): string => new Date().toISOString().slice(0, 10);

interface ViewDetailCellProps {
  run: NightAuditRunResponse;
  onView: (run: NightAuditRunResponse) => void;
  t: TFunction;
}

const ViewDetailCell = ({ run, onView, t }: ViewDetailCellProps) => {
  const handleClick = useCallback(() => {
    onView(run);
  }, [onView, run]);

  return (
    <button
      type="button"
      className="text-primary hover:underline text-sm font-medium"
      onClick={handleClick}
    >
      {t('view')}
    </button>
  );
};

export const NightAudit = () => {
  const { t, i18n } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);

  const [page, setPage] = useState(0);
  const [runDate, setRunDate] = useState(() => {
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    return yesterday.toISOString().slice(0, 10);
  });
  const [confirmingRun, setConfirmingRun] = useState(false);
  const [detailRun, setDetailRun] = useState<NightAuditRunResponse | null>(null);

  const { data: historyPage, isLoading, error: queryError, refetch } = useNightAuditHistory(page, PAGE_SIZE);
  const runs = historyPage?.content ?? [];
  const totalPages = historyPage?.totalPages ?? 1;
  const error = queryError ? getErrorMessage(queryError, t('night_audit_load_failed')) : null;

  const formatCurrency = useCallback((amount: number) =>
    new Intl.NumberFormat(i18n.language, { style: 'currency', currency: 'EUR' }).format(amount),
  [i18n.language]);

  const runMutation = useRunNightAudit();

  const handleRunDateChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setRunDate(e.target.value);
  }, []);

  const handleRunRequest = useCallback(() => {
    setConfirmingRun(true);
  }, []);

  const handleRunDialogClose = useCallback(() => {
    setConfirmingRun(false);
  }, []);

  const handleRunConfirm = useCallback(async () => {
    try {
      const result = await runMutation.mutateAsync(runDate);
      if (result.status === 'COMPLETED') {
        addToast(t('night_audit_run_success', { date: result.businessDate }), 'success');
      } else {
        addToast(t('night_audit_run_failed_detail', { reason: result.failureReason ?? '' }), 'error');
      }
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('night_audit_run_failed')), 'error');
    } finally {
      setConfirmingRun(false);
    }
  }, [runDate, runMutation, addToast, t]);

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
      cell: ({ row }) => <span className="font-medium">{row.original.businessDate}</span>,
    },
    {
      id: 'status',
      header: t('status'),
      cell: ({ row }) => (
        <M3StatusChip label={getStatusLabel(row.original.status, t)} tone={getStatusTone(row.original.status)} />
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
                <MaterialIcon name="warning" size={16} className="text-warning" />
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
      cell: ({ row }) => <ViewDetailCell run={row.original} onView={handleViewDetail} t={t} />,
    },
  ], [t, formatCurrency, handleViewDetail]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center">
            <MaterialIcon name="fact_check" className="mr-2 text-primary" />
            {t('nav_night_audit')}
          </h1>
          <p className="text-sm font-body text-on-surface-variant mt-1">{t('night_audit_subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <M3TextField
            label={t('night_audit_business_date')}
            type="date"
            value={runDate}
            onChange={handleRunDateChange}
            max={todayIsoDate()}
          />
          <M3Button icon="play_arrow" onClick={handleRunRequest} loading={runMutation.isPending}>
            {t('night_audit_run_action')}
          </M3Button>
        </div>
      </div>

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
        />
      )}

      {confirmingRun && (
        <M3Dialog
          open
          title={t('night_audit_run_action')}
          titleId="confirm-night-audit-run-dialog"
          onClose={handleRunDialogClose}
        >
          <p className="text-sm font-body text-on-surface">
            {t('night_audit_run_confirm', { date: runDate })}
          </p>
          <div className="flex justify-end gap-3 pt-4">
            <M3Button type="button" variant="outlined" onClick={handleRunDialogClose} disabled={runMutation.isPending}>
              {t('cancel')}
            </M3Button>
            <M3Button type="button" onClick={handleRunConfirm} loading={runMutation.isPending}>
              {t('confirm')}
            </M3Button>
          </div>
        </M3Dialog>
      )}

      {detailRun && (
        <M3Dialog
          open
          title={t('night_audit_detail_title', { date: detailRun.businessDate })}
          titleId="night-audit-detail-dialog"
          onClose={handleDetailClose}
        >
          <div className="space-y-3 text-sm font-body text-on-surface">
            <div className="flex justify-between">
              <span className="text-on-surface-variant">{t('status')}</span>
              <M3StatusChip label={getStatusLabel(detailRun.status, t)} tone={getStatusTone(detailRun.status)} />
            </div>
            {detailRun.status === 'FAILED' && detailRun.failureReason && (
              <p className="text-error">{detailRun.failureReason}</p>
            )}
            {detailRun.status === 'COMPLETED' && (
              <>
                <div className="flex justify-between">
                  <span className="text-on-surface-variant">{t('night_audit_guests_in_house')}</span>
                  <span>{detailRun.guestsInHouse}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-on-surface-variant">{t('night_audit_current_stays')}</span>
                  <span>{detailRun.currentStays}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-on-surface-variant">{t('night_audit_available_rooms')}</span>
                  <span>{detailRun.availableRooms}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-on-surface-variant">{t('night_audit_no_shows_marked')}</span>
                  <span>{detailRun.noShowsMarked}</span>
                </div>
                <hr className="border-outline-variant" />
                <p className="font-medium">
                  {t('night_audit_cash_total')}
                  {detailRun.cashSummaryDegraded && (
                    <span className="ml-2 text-warning text-xs">{t('night_audit_cash_degraded')}</span>
                  )}
                </p>
                {detailRun.cashByMethod.length === 0 ? (
                  <p className="text-on-surface-variant">{t('night_audit_no_cash_activity')}</p>
                ) : (
                  <ul className="space-y-1">
                    {detailRun.cashByMethod.map((line) => (
                      <li key={line.paymentMethod} className="flex justify-between">
                        <span className="text-on-surface-variant">{line.paymentMethod}</span>
                        <span>{formatCurrency(line.total)}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </>
            )}
          </div>
          <div className="flex justify-end pt-4">
            <M3Button type="button" variant="outlined" onClick={handleDetailClose}>
              {t('close')}
            </M3Button>
          </div>
        </M3Dialog>
      )}
    </div>
  );
};
