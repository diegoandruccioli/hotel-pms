import { useState, useEffect, useCallback, memo, useMemo } from 'react';
import type { InvoiceResponse, InvoiceStatus } from '../types/billing.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3Table, M3TableRow, M3TableCell } from '../components/m3/M3Table';
import { M3StatusChip } from '../components/m3/M3StatusChip';
import { billingService } from '../services/billingService';
import { PaymentModal } from './Billing/PaymentModal';
import { InvoiceDetailModal } from './Billing/InvoiceDetailModal';
import { useTranslation } from 'react-i18next';

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
  invoice: InvoiceResponse;
  onView: (inv: InvoiceResponse) => void;
  onPay: (inv: InvoiceResponse) => void;
  formatDate: (d?: string) => string;
  formatCurrency: (n: number) => string;
  tView: string;
  tRegisterPayment: string;
  tPending: string;
}

const InvoiceRow = memo(({
  invoice,
  onView,
  onPay,
  formatDate,
  formatCurrency,
  tView,
  tRegisterPayment,
  tPending,
}: InvoiceRowProps) => {
  const { t } = useTranslation('common');
  const handleView = useCallback(() => onView(invoice), [onView, invoice]);
  const handlePay  = useCallback(() => onPay(invoice),  [onPay,  invoice]);

  return (
    <M3TableRow>
      <M3TableCell className="font-medium">
        {invoice.invoiceNumber || (
          <span className="text-on-surface-variant italic">{tPending}</span>
        )}
      </M3TableCell>
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

export const Billing = memo(() => {
  const { t, i18n } = useTranslation('common');
  const [invoices, setInvoices] = useState<InvoiceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [paymentTarget, setPaymentTarget] = useState<InvoiceResponse | null>(null);
  const [detailTarget, setDetailTarget]   = useState<InvoiceResponse | null>(null);

  const loadInvoices = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await billingService.getAllInvoices();
      setInvoices(data);
    } catch (err: unknown) {
      const e = err as { response?: { data?: { detail?: string } }; message?: string };
      setError(e.response?.data?.detail ?? e.message ?? t('failed_load_invoices'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    loadInvoices();
  }, [loadInvoices]);

  const handlePaid = useCallback((updated: InvoiceResponse) => {
    setInvoices((prev) => prev.map((inv) => (inv.id === updated.id ? updated : inv)));
  }, []);

  const handleOpenDetail  = useCallback((inv: InvoiceResponse) => setDetailTarget(inv), []);
  const handleOpenPayment = useCallback((inv: InvoiceResponse) => setPaymentTarget(inv), []);
  const handleCloseDetail  = useCallback(() => setDetailTarget(null), []);
  const handleClosePayment = useCallback(() => setPaymentTarget(null), []);

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
          {invoices.length === 0 ? (
            <tr>
              <td colSpan={5} className="py-8 text-center text-sm font-body text-on-surface-variant">
                {t('no_invoices')}
              </td>
            </tr>
          ) : (
            invoices.map((invoice) => (
              <InvoiceRow
                key={invoice.id}
                invoice={invoice}
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
        />
      )}
    </div>
  );
});

Billing.displayName = 'Billing';
