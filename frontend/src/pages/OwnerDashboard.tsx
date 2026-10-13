import { useFormatters } from '../hooks';
import { useState, useCallback, useMemo, memo } from 'react';
import { billingReportService } from '../services';
import { useAuthStore } from '../store';
import { useToastStore } from '../store';
import type { OwnerFinancialReportDto, OwnerFinancialSummaryDto } from '../types';
import type { InvoiceResponse } from '../types';
import { MaterialIcon } from '../components/MaterialIcon';
import { Alert } from '../components/Alert';
import { PageHeader } from '../components/PageHeader';
import { M3Button } from '../components/m3';
import { M3TableEmptyRow } from '../components/m3';
import { M3Card } from '../components/m3';
import { M3Table, M3TableRow, M3TableCell } from '../components/m3';
import { M3StatusChip } from '../components/m3';
import { M3TextField } from '../components/m3';
import { useTranslation } from 'react-i18next';
import { getErrorMessage, invoiceStatusTone, todayIsoDate, toIsoDate } from '../utils';
import { KpiTrendSection } from './OwnerDashboard/KpiTrendSection';
import { OwnerKpiCards } from './OwnerDashboard/OwnerKpiCards';

/** Same-length period immediately preceding `[start, end]` (both inclusive),
 * e.g. 2026-09-01..2026-09-07 (7 days) -> 2026-08-25..2026-08-31 — used to
 * give the KPI cards a comparison instead of a bare absolute figure, per the
 * "pace/flash report" convention every PMS reviewed uses (Mews, Cloudbeds
 * Insights, OPERA). No backend `compareWith` param exists, so this fetches
 * the same aggregates-only summary endpoint a second time. */
const getPreviousPeriod = (startDate: string, endDate: string): { prevStart: string; prevEnd: string } => {
  const start = new Date(`${startDate}T00:00:00`);
  const end = new Date(`${endDate}T00:00:00`);
  const lengthDays = Math.round((end.getTime() - start.getTime()) / 86_400_000) + 1;
  const prevEndDate = new Date(start);
  prevEndDate.setDate(prevEndDate.getDate() - 1);
  const prevStartDate = new Date(prevEndDate);
  prevStartDate.setDate(prevStartDate.getDate() - (lengthDays - 1));
  return { prevStart: toIsoDate(prevStartDate), prevEnd: toIsoDate(prevEndDate) };
};

const getFirstDayOfMonth = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`;
};

const InvoiceRow = memo(({
  inv,
  formatDate,
  formatCurrency,
  formatStatus,
}: {
  inv: InvoiceResponse;
  formatDate: (d?: string) => string;
  formatCurrency: (amount: number) => string;
  formatStatus: (status: InvoiceResponse['status']) => string;
}) => (
  <M3TableRow key={inv.id}>
    <M3TableCell className="font-medium">{inv.invoiceNumber}</M3TableCell>
    <M3TableCell className="text-on-surface-variant">{formatDate(inv.issueDate)}</M3TableCell>
    <M3TableCell className="font-medium">{formatCurrency(inv.totalAmount)}</M3TableCell>
    <M3TableCell>
      <M3StatusChip label={formatStatus(inv.status)} tone={invoiceStatusTone[inv.status]} />
    </M3TableCell>
  </M3TableRow>
));

InvoiceRow.displayName = 'InvoiceRow';

export const OwnerDashboard = memo(() => {
  const { t } = useTranslation('common');
  const { user } = useAuthStore();
  const addToast = useToastStore((s) => s.addToast);
  const [startDate, setStartDate] = useState(getFirstDayOfMonth());
  const [endDate, setEndDate] = useState(todayIsoDate());
  const [report, setReport] = useState<OwnerFinancialReportDto | null>(null);
  const [previousSummary, setPreviousSummary] = useState<OwnerFinancialSummaryDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { formatCurrency, formatDate } = useFormatters();

  const formatStatus = useCallback((status: InvoiceResponse['status']) =>
    t(`invoice_status_${status}`),
  [t]);

  const isAuthorized = user?.role === 'OWNER' || user?.role === 'ADMIN';

  const loadReport = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { prevStart, prevEnd } = getPreviousPeriod(startDate, endDate);
      const [data, previous] = await Promise.all([
        billingReportService.getOwnerFinancialReport(startDate, endDate),
        // Best-effort: a comparison is a nice-to-have, not worth failing the
        // whole report load over (e.g. prevStart predating the hotel's data).
        billingReportService.getOwnerFinancialSummary(prevStart, prevEnd).catch(() => null),
      ]);
      setReport(data);
      setPreviousSummary(previous);
    } catch (err: unknown) {
      const message = getErrorMessage(err, t('failed_load_report'));
      setError(message);
      addToast(message, 'error');
    } finally {
      setLoading(false);
    }
  }, [startDate, endDate, addToast, t]);

  const handleExport = useCallback(async () => {
    if (!report) return;
    try {
      await billingReportService.exportToCsv(startDate, endDate);
      addToast(t('csv_export_started'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('csv_export_failed')), 'error');
    }
  }, [report, startDate, endDate, addToast, t]);

  const handleStartDateChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setStartDate(e.target.value);
  }, []);

  const handleEndDateChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setEndDate(e.target.value);
  }, []);

  const tableHeaders = useMemo(() => [t('invoice_number'), t('issue_date'), t('amount'), t('status')], [t]);

  if (!isAuthorized) {
    return (
      <div className="flex flex-col items-center justify-center h-64 space-y-4">
        <MaterialIcon name="gpp_maybe" size={64} className="text-error" />
        <h2 className="text-xl font-display font-semibold text-on-surface">{t('access_restricted')}</h2>
        <p className="text-sm font-body text-on-surface-variant">{t('area_reserved_owner_admin')}</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        icon="bar_chart"
        title={t('owner_dashboard')}
        subtitle={t('owner_dashboard_subtitle')}
        actions={
          report && (
            <M3Button variant="tonal" icon="download" id="export-csv-btn" onClick={handleExport}>
              {t('export_csv')}
            </M3Button>
          )
        }
      />

      <M3Card variant="outlined" className="p-4">
        <div className="flex flex-col sm:flex-row items-end gap-4">
          <M3TextField
            className="flex-1"
            label={t('start_date')}
            type="date"
            value={startDate}
            onChange={handleStartDateChange}
          />
          <M3TextField
            className="flex-1"
            label={t('end_date')}
            type="date"
            value={endDate}
            onChange={handleEndDateChange}
          />
          <M3Button
            id="load-report-btn"
            icon={loading ? 'progress_activity' : 'bar_chart'}
            loading={loading}
            disabled={loading}
            onClick={loadReport}
          >
            {t('generate_report')}
          </M3Button>
        </div>
      </M3Card>

      {error && (
        <Alert tone="error">{error}</Alert>
      )}

      <KpiTrendSection
        startDate={startDate}
        endDate={endDate}
        formatCurrency={formatCurrency}
        formatDate={formatDate}
      />

      {report && (
        <>
          <OwnerKpiCards report={report} previousSummary={previousSummary} formatCurrency={formatCurrency} />

          <M3Table headers={tableHeaders}>
            {report.invoices.length === 0 ? (
              <M3TableEmptyRow colSpan={tableHeaders.length} message={t('no_invoices_period')} />
            ) : (
              report.invoices.map((inv) => (
                <InvoiceRow
                  key={inv.id}
                  inv={inv}
                  formatDate={formatDate}
                  formatCurrency={formatCurrency}
                  formatStatus={formatStatus}
                />
              ))
            )}
          </M3Table>
        </>
      )}
    </div>
  );
});
