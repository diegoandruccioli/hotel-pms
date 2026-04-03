import { useState, useCallback, useMemo, memo } from 'react';
import { billingReportService } from '../services/billingReportService';
import { useAuthStore } from '../store/authStore';
import { useToastStore } from '../store/toastStore';
import type { OwnerFinancialReportDto } from '../types/ownerReport.types';
import type { InvoiceResponse } from '../types/billing.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3Card } from '../components/m3/M3Card';
import { M3Table, M3TableRow, M3TableCell } from '../components/m3/M3Table';
import { M3StatusChip } from '../components/m3/M3StatusChip';
import { useTranslation } from 'react-i18next';

const getStatusTone = (status: InvoiceResponse['status']) => {
  switch (status) {
    case 'PAID': return 'success' as const;
    case 'ISSUED': return 'warning' as const;
    case 'CANCELLED': return 'error' as const;
    default: return 'neutral' as const;
  }
};

const getFirstDayOfMonth = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`;
};

const getTodayString = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

const InvoiceRow = memo(({ 
  inv, 
  formatDate, 
  formatCurrency 
}: { 
  inv: InvoiceResponse;
  formatDate: (d?: string) => string;
  formatCurrency: (amount: number) => string;
}) => (
  <M3TableRow key={inv.id}>
    <M3TableCell className="font-medium">{inv.invoiceNumber}</M3TableCell>
    <M3TableCell className="text-on-surface-variant">{formatDate(inv.issueDate)}</M3TableCell>
    <M3TableCell className="font-medium">{formatCurrency(inv.totalAmount)}</M3TableCell>
    <M3TableCell>
      <M3StatusChip label={inv.status} tone={getStatusTone(inv.status)} />
    </M3TableCell>
  </M3TableRow>
));

InvoiceRow.displayName = 'InvoiceRow';

export const OwnerDashboard = memo(() => {
  const { t, i18n } = useTranslation('common');
  const { user } = useAuthStore();
  const addToast = useToastStore((s) => s.addToast);
  const [startDate, setStartDate] = useState(getFirstDayOfMonth());
  const [endDate, setEndDate] = useState(getTodayString());
  const [report, setReport] = useState<OwnerFinancialReportDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const formatCurrency = useCallback((amount: number) =>
    new Intl.NumberFormat(i18n.language, { style: 'currency', currency: 'EUR' }).format(amount),
  [i18n.language]);

  const formatDate = useCallback((dateStr?: string) => {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString(i18n.language);
  }, [i18n.language]);

  const isAuthorized = user?.role === 'OWNER' || user?.role === 'ADMIN';

  const loadReport = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await billingReportService.getOwnerFinancialReport(startDate, endDate);
      setReport(data);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : t('failed_load_report');
      setError(message);
      addToast(message, 'error');
    } finally {
      setLoading(false);
    }
  }, [startDate, endDate, addToast, t]);

  const handleExport = useCallback(() => {
    if (!report) return;
    billingReportService.exportToCsv(report);
    addToast(t('csv_export_started'), 'success');
  }, [report, addToast, t]);

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
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center">
            <MaterialIcon name="bar_chart" className="mr-2 text-primary" />
            {t('owner_dashboard')}
          </h1>
          <p className="text-sm font-body text-on-surface-variant mt-1">{t('owner_dashboard_subtitle')}</p>
        </div>
        {report && (
          <M3Button variant="tonal" icon="download" id="export-csv-btn" onClick={handleExport}>
            {t('export_csv')}
          </M3Button>
        )}
      </div>

      <M3Card variant="outlined" className="p-4">
        <div className="flex flex-col sm:flex-row items-end gap-4">
          <div className="flex-1">
            <label htmlFor="startDate" className="block text-sm font-medium font-body text-on-surface-variant mb-1">
              {t('start_date')}
            </label>
            <input
              id="startDate"
              type="date"
              value={startDate}
              onChange={handleStartDateChange}
              className="block w-full rounded-shape-xs border border-outline px-3 py-2 text-sm font-body bg-transparent text-on-surface focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none"
            />
          </div>
          <div className="flex-1">
            <label htmlFor="endDate" className="block text-sm font-medium font-body text-on-surface-variant mb-1">
              {t('end_date')}
            </label>
            <input
              id="endDate"
              type="date"
              value={endDate}
              onChange={handleEndDateChange}
              className="block w-full rounded-shape-xs border border-outline px-3 py-2 text-sm font-body bg-transparent text-on-surface focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none"
            />
          </div>
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
        <div className="flex items-center gap-3 px-4 py-3 rounded-shape-sm bg-error-container text-on-error-container">
          <MaterialIcon name="error" size={20} className="flex-shrink-0" />
          <p className="text-sm font-body">{error}</p>
        </div>
      )}

      {report && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <M3Card variant="glass" className="p-5 flex items-center gap-4">
              <div className="flex items-center justify-center w-12 h-12 rounded-shape-lg bg-primary-container">
                <MaterialIcon name="trending_up" size={24} className="text-on-primary-container" />
              </div>
              <div>
                <p className="text-sm font-body text-on-surface-variant">{t('total_revenue')}</p>
                <p className="text-2xl font-display font-bold text-on-surface">{formatCurrency(report.totalRevenue)}</p>
              </div>
            </M3Card>
            <M3Card variant="glass" className="p-5 flex items-center gap-4">
              <div className="flex items-center justify-center w-12 h-12 rounded-shape-lg bg-secondary-container">
                <MaterialIcon name="description" size={24} className="text-on-secondary-container" />
              </div>
              <div>
                <p className="text-sm font-body text-on-surface-variant">{t('total_invoices')}</p>
                <p className="text-2xl font-display font-bold text-on-surface">{report.totalInvoices}</p>
              </div>
            </M3Card>
            <M3Card variant="glass" className="p-5 flex items-center gap-4">
              <div className="flex items-center justify-center w-12 h-12 rounded-shape-lg bg-tertiary-container">
                <MaterialIcon name="verified" size={24} className="text-on-tertiary-container" />
              </div>
              <div>
                <p className="text-sm font-body text-on-surface-variant">{t('paid_invoices')}</p>
                <p className="text-2xl font-display font-bold text-on-surface">{report.paidInvoices}</p>
                <p className="text-xs font-body text-on-surface-variant">
                  {report.totalInvoices > 0
                    ? `${Math.round((report.paidInvoices / report.totalInvoices) * 100)}% ${t('collection_rate')}`
                    : t('no_invoices')}
                </p>
              </div>
            </M3Card>
          </div>

          <M3Table headers={tableHeaders}>
            {report.invoices.length === 0 ? (
              <tr><td colSpan={4} className="py-8 text-center text-sm font-body text-on-surface-variant">{t('no_invoices_period')}</td></tr>
            ) : (
              report.invoices.map((inv) => (
                <InvoiceRow 
                  key={inv.id} 
                  inv={inv} 
                  formatDate={formatDate}
                  formatCurrency={formatCurrency}
                />
              ))
            )}
          </M3Table>
        </>
      )}
    </div>
  );
});
