import { useCallback, memo, useState } from 'react';
import { billingService } from '../../services/billingService';

const ICON_STYLE: React.CSSProperties = { fontSize: 18 };
import { useTranslation } from 'react-i18next';
import { M3Dialog } from '../../components/m3/M3Dialog';
import { M3StatusChip } from '../../components/m3/M3StatusChip';
import { useToastStore } from '../../store/toastStore';
import type { InvoiceResponse, InvoiceStatus, PaymentMethod, ChargeType } from '../../types/billing.types';

interface Props {
  invoice: InvoiceResponse;
  onClose: () => void;
}

const statusTone = (s: InvoiceStatus) => {
  if (s === 'PAID') return 'success' as const;
  if (s === 'CANCELLED') return 'error' as const;
  return 'warning' as const;
};

const methodIcon: Record<PaymentMethod, string> = {
  CASH: 'payments',
  CREDIT_CARD: 'credit_card',
  DEBIT_CARD: 'credit_card',
  BANK_TRANSFER: 'account_balance',
  CHECK: 'receipt',
};

const chargeTypeIcon: Record<ChargeType, string> = {
  FB_ORDER: 'restaurant',
  ROOM_SERVICE: 'room_service',
  OTHER: 'add_circle',
};

export const InvoiceDetailModal = memo(({ invoice, onClose }: Props) => {
  const { t, i18n } = useTranslation(['billing', 'common']);
  const { addToast } = useToastStore();
  const [downloading, setDownloading] = useState(false);

  const handleDownloadPdf = useCallback(async () => {
    setDownloading(true);
    try {
      await billingService.downloadPdf(invoice.id, invoice.invoiceNumber);
    } catch {
      addToast(t('err_pdf_download', { ns: 'billing' }), 'error');
    } finally {
      setDownloading(false);
    }
  }, [invoice.id, invoice.invoiceNumber, addToast, t]);

  const formatCurrency = useCallback(
    (val: number) =>
      new Intl.NumberFormat(i18n.language, {
        style: 'currency',
        currency: 'EUR',
      }).format(val),
    [i18n.language],
  );

  const formatDateTime = useCallback(
    (dateStr?: string) => {
      if (!dateStr) return '—';
      return new Date(dateStr).toLocaleString(i18n.language);
    },
    [i18n.language],
  );

  const totalPaid = invoice.payments.reduce((sum, p) => sum + p.amount, 0);

  return (
    <M3Dialog
      open
      title={t('invoice_detail_title', { ns: 'billing' })}
      titleId="invoice-detail-title"
      onClose={onClose}
    >
      <div className="space-y-6 text-sm font-body">
        {/* Header summary */}
        <dl className="grid grid-cols-2 gap-x-6 gap-y-3">
          <div>
            <dt className="text-on-surface-variant text-xs">{t('invoice_number', { ns: 'common' })}</dt>
            <dd className="font-medium text-on-surface">{invoice.invoiceNumber || '—'}</dd>
          </div>
          <div>
            <dt className="text-on-surface-variant text-xs">{t('issue_date', { ns: 'common' })}</dt>
            <dd className="text-on-surface">{formatDateTime(invoice.issueDate)}</dd>
          </div>
          <div>
            <dt className="text-on-surface-variant text-xs">{t('total_amount', { ns: 'common' })}</dt>
            <dd className="font-semibold text-on-surface text-base">
              {formatCurrency(invoice.totalAmount)}
            </dd>
          </div>
          <div>
            <dt className="text-on-surface-variant text-xs">{t('status', { ns: 'common' })}</dt>
            <dd className="mt-0.5">
              <M3StatusChip
                label={t(`invoice_status_${invoice.status}`, { ns: 'common', defaultValue: invoice.status })}
                tone={statusTone(invoice.status)}
              />
            </dd>
          </div>
        </dl>

        {/* Charges (F&B) */}
        {invoice.charges && invoice.charges.length > 0 && (
          <section aria-labelledby="charges-heading">
            <h3 id="charges-heading" className="text-xs font-medium text-on-surface-variant uppercase tracking-wide mb-2">
              {t('charges', { ns: 'billing' })}
            </h3>
            <ul className="divide-y divide-outline-variant">
              {invoice.charges.map((charge) => (
                <li key={charge.id} className="flex items-center justify-between py-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="material-symbols-outlined text-on-surface-variant shrink-0" style={ICON_STYLE}>
                      {chargeTypeIcon[charge.type] ?? 'receipt'}
                    </span>
                    <span className="truncate text-on-surface">
                      {charge.description || t(`charge_type_${charge.type.toLowerCase()}`, { ns: 'billing' })}
                    </span>
                  </div>
                  <span className="font-medium text-on-surface shrink-0 ml-4">
                    {formatCurrency(charge.amount)}
                  </span>
                </li>
              ))}
            </ul>
          </section>
        )}

        {/* Payments history */}
        <section aria-labelledby="payments-heading">
          <h3 id="payments-heading" className="text-xs font-medium text-on-surface-variant uppercase tracking-wide mb-2">
            {t('payments_history', { ns: 'billing' })}
          </h3>
          {invoice.payments.length === 0 ? (
            <p className="text-on-surface-variant italic">{t('no_payments_yet', { ns: 'billing' })}</p>
          ) : (
            <>
              <ul className="divide-y divide-outline-variant">
                {invoice.payments.map((p) => (
                  <li key={p.id} className="flex items-center justify-between py-2">
                    <div className="flex items-center gap-2 min-w-0">
                      <span className="material-symbols-outlined text-on-surface-variant shrink-0" style={ICON_STYLE}>
                        {methodIcon[p.paymentMethod] ?? 'payments'}
                      </span>
                      <div className="min-w-0">
                        <p className="text-on-surface">
                          {t(`payment_method_${p.paymentMethod.toLowerCase()}`, { ns: 'billing' })}
                        </p>
                        <p className="text-xs text-on-surface-variant">
                          {formatDateTime(p.paymentDate)}
                          {p.transactionReference && ` · ${p.transactionReference}`}
                        </p>
                      </div>
                    </div>
                    <span className="font-medium text-success shrink-0 ml-4">
                      {formatCurrency(p.amount)}
                    </span>
                  </li>
                ))}
              </ul>
              <div className="flex justify-between pt-3 border-t border-outline-variant font-medium">
                <span className="text-on-surface-variant">{t('total_paid', { ns: 'billing' })}</span>
                <span className="text-on-surface">{formatCurrency(totalPaid)}</span>
              </div>
            </>
          )}
        </section>
      </div>

      {/* PDF download action */}
      <div className="flex justify-end pt-2 border-t border-outline-variant mt-4">
        <button
          type="button"
          onClick={handleDownloadPdf}
          disabled={downloading}
          aria-busy={downloading}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-primary text-on-primary text-sm font-medium hover:opacity-90 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50 disabled:cursor-not-allowed min-h-[40px]"
        >
          <span className="material-symbols-outlined" style={ICON_STYLE} aria-hidden="true">
            download
          </span>
          {downloading
            ? t('pdf_downloading', { ns: 'billing' })
            : t('download_pdf', { ns: 'billing' })}
        </button>
      </div>
    </M3Dialog>
  );
});

InvoiceDetailModal.displayName = 'InvoiceDetailModal';
