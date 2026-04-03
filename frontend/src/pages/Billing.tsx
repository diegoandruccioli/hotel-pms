import { useState, useEffect, useCallback, memo, useMemo } from 'react';
import type { InvoiceResponse, InvoiceStatus } from '../types/billing.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3Table, M3TableRow, M3TableCell } from '../components/m3/M3Table';
import { M3StatusChip } from '../components/m3/M3StatusChip';
import { billingService } from '../services/billingService';
import { useTranslation } from 'react-i18next';

const getStatusTone = (status: InvoiceStatus) => {
  switch (status) {
    case 'ISSUED': return 'warning' as const;
    case 'PAID': return 'success' as const;
    case 'CANCELLED': return 'error' as const;
    default: return 'neutral' as const;
  }
};

export const Billing = memo(() => {
  const { t, i18n } = useTranslation('common');
  const [invoices, setInvoices] = useState<InvoiceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadInvoices = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await billingService.getAllInvoices();
      setInvoices(data);
    } catch (err: unknown) {
      const e = err as {response?: {data?: {detail?: string}}, message?: string};
      setError(e.response?.data?.detail || e.message || t('failed_load_invoices'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    loadInvoices();
  }, [loadInvoices]);

  const formatCurrency = useCallback((amount: number) => {
    return new Intl.NumberFormat(i18n.language, { style: 'currency', currency: 'USD' }).format(amount);
  }, [i18n.language]);

  const formatDate = useCallback((dateStr?: string) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleDateString(i18n.language);
  }, [i18n.language]);

  const tableHeaders = useMemo(() => [
    t('invoice_number'), 
    t('issue_date'), 
    t('total_amount'), 
    t('status'), 
    <span key="sr" className="sr-only">{t('actions')}</span>
  ], [t]);

  const InvoiceRow = memo(({ invoice }: { invoice: InvoiceResponse }) => (
    <M3TableRow key={invoice.id}>
      <M3TableCell className="font-medium">
        {invoice.invoiceNumber || <span className="text-on-surface-variant italic">{t('pending')}</span>}
      </M3TableCell>
      <M3TableCell className="text-on-surface-variant">{formatDate(invoice.issueDate)}</M3TableCell>
      <M3TableCell className="font-medium">{formatCurrency(invoice.totalAmount)}</M3TableCell>
      <M3TableCell>
        <M3StatusChip label={invoice.status} tone={getStatusTone(invoice.status)} />
      </M3TableCell>
      <M3TableCell className="text-right">
        <button className="text-primary hover:text-primary/80 font-medium text-sm mr-4">{t('view')}</button>
        {invoice.status !== 'PAID' && (
          <button className="text-tertiary hover:text-tertiary/80 font-medium text-sm">{t('register_payment')}</button>
        )}
      </M3TableCell>
    </M3TableRow>
  ));

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
        <M3Button icon="add">{t('create_invoice')}</M3Button>
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
            <button type="button" onClick={loadInvoices} className="mt-2 text-sm font-medium underline hover:no-underline">
              {t('try_again')}
            </button>
          </div>
        </div>
      ) : (
        <M3Table headers={tableHeaders}>
          {invoices.length === 0 ? (
            <tr><td colSpan={5} className="py-8 text-center text-sm font-body text-on-surface-variant">{t('no_invoices')}</td></tr>
          ) : (
            invoices.map((invoice) => (
              <InvoiceRow key={invoice.id} invoice={invoice} />
            ))
          )}
        </M3Table>
      )}
    </div>
  );
});
