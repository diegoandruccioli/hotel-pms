import { useCallback, memo, useState } from 'react';
import { billingService } from '../../services';
import { useToastStore } from '../../store';
import { useTranslation } from 'react-i18next';
import { M3Dialog } from '../../components/m3';
import { M3StatusChip } from '../../components/m3';
import { MaterialIcon } from '../../components/MaterialIcon';
import { AddChargeModal } from './AddChargeModal';
import { getErrorMessage } from '../../utils';
import type { BillingDocumentType as DocumentType, InvoiceResponse, InvoiceStatus, PaymentMethod, ChargeType, SdiStatus } from '../../types';

interface Props {
  invoice: InvoiceResponse;
  onClose: () => void;
  onUpdated?: (updated: InvoiceResponse) => void;
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
  ROOM_NIGHT: 'bed',
  EXTRA: 'add_circle',
  CITY_TAX: 'account_balance',
};

const sdiStatusTone = (s: SdiStatus) => {
  if (s === 'ACCEPTED') return 'success' as const;
  if (s === 'REJECTED') return 'error' as const;
  if (s === 'SENT') return 'warning' as const;
  return 'neutral' as const;
};

interface ChargeRowProps {
  charge: NonNullable<InvoiceResponse['charges']>[number];
  removable: boolean;
  removing: boolean;
  formatCurrency: (val: number) => string;
  onRemove: (chargeId: string, amount: number) => void;
}

/** Extracted so the per-row remove handler can close over this row's own
 * charge id/amount via useCallback, instead of an inline arrow created fresh
 * on every render of the parent's .map() (react-perf/jsx-no-new-function-as-prop). */
const ChargeRow = memo(({ charge, removable, removing, formatCurrency, onRemove }: ChargeRowProps) => {
  const { t } = useTranslation('billing');
  const handleRemoveClick = useCallback(
    () => onRemove(charge.id, charge.amount),
    [onRemove, charge.id, charge.amount],
  );

  return (
    <li className="flex items-center justify-between py-2">
      <div className="flex items-center gap-2 min-w-0">
        <MaterialIcon
          name={chargeTypeIcon[charge.type] ?? 'receipt'}
          size={18}
          className="text-on-surface-variant shrink-0"
        />
        <span className="truncate text-on-surface">
          {charge.description || t(`charge_type_${charge.type.toLowerCase()}`)}
        </span>
      </div>
      <div className="flex items-center gap-2 shrink-0 ml-4">
        <span className="font-medium text-on-surface">
          {formatCurrency(charge.amount)}
        </span>
        {removable && (
          <button
            type="button"
            aria-label={t('remove_charge')}
            disabled={removing}
            onClick={handleRemoveClick}
            className="text-on-surface-variant hover:text-error disabled:opacity-50 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-sm min-h-10 min-w-10 flex items-center justify-center"
          >
            <MaterialIcon name="delete" size={18} />
          </button>
        )}
      </div>
    </li>
  );
});

ChargeRow.displayName = 'ChargeRow';

export const InvoiceDetailModal = memo(({ invoice, onClose, onUpdated }: Props) => {
  const { t, i18n } = useTranslation(['billing', 'common']);
  const addToast = useToastStore((s) => s.addToast);
  const [switchingType, setSwitchingType] = useState(false);
  const [validatingXml, setValidatingXml] = useState(false);
  const [addingCharge, setAddingCharge] = useState(false);
  const [removingChargeId, setRemovingChargeId] = useState<string | null>(null);

  const handleDownloadPdf = useCallback(() => {
    billingService.downloadPdf(invoice.id);
  }, [invoice.id]);

  // The actual download (below) fires via a hidden iframe (see billingService), which has
  // no way to observe an HTTP error response — a legitimate rejection (e.g. incomplete
  // guest address) would otherwise fail completely silently. Validate first, over a real
  // XHR that can surface the error as a toast, and only trigger the iframe on success.
  const handleDownloadFatturaPAXml = useCallback(async () => {
    setValidatingXml(true);
    try {
      await billingService.validateFatturaPAXml(invoice.id);
      billingService.downloadFatturaPAXml(invoice.id);
    } catch (err: unknown) {
      const e = err as { response?: { data?: { detail?: string } }; message?: string };
      addToast(e.response?.data?.detail ?? e.message ?? t('toast_error', { ns: 'common' }), 'error');
    } finally {
      setValidatingXml(false);
    }
  }, [invoice.id, addToast, t]);

  const handleDownloadFatturaPAXmlVoid = useCallback(
    () => { void handleDownloadFatturaPAXml(); },
    [handleDownloadFatturaPAXml],
  );

  const handleToggleDocumentType = useCallback(async () => {
    const next: DocumentType = invoice.documentType === 'FATTURA' ? 'RICEVUTA' : 'FATTURA';
    setSwitchingType(true);
    try {
      const updated = await billingService.updateDocumentType(invoice.id, next);
      onUpdated?.(updated);
      addToast(t('document_type_updated', { ns: 'billing' }), 'success');
    } catch (err: unknown) {
      const e = err as { response?: { data?: { detail?: string } }; message?: string };
      addToast(e.response?.data?.detail ?? e.message ?? t('toast_error', { ns: 'common' }), 'error');
    } finally {
      setSwitchingType(false);
    }
  }, [invoice.id, invoice.documentType, onUpdated, addToast, t]);

  const handleToggleDocumentTypeVoid = useCallback(
    () => { void handleToggleDocumentType(); },
    [handleToggleDocumentType],
  );

  const handleOpenAddCharge = useCallback(() => setAddingCharge(true), []);
  const handleCloseAddCharge = useCallback(() => setAddingCharge(false), []);
  const handleChargeAdded = useCallback(
    (updated: InvoiceResponse) => { onUpdated?.(updated); },
    [onUpdated],
  );

  const handleRemoveChargeAsync = useCallback(async (chargeId: string, amount: number) => {
    if (!window.confirm(t('confirm_remove_charge', { ns: 'billing' }))) {
      return;
    }
    if (!invoice.stayId) {
      return;
    }
    setRemovingChargeId(chargeId);
    try {
      await billingService.removeCharge(invoice.stayId, chargeId);
      onUpdated?.({
        ...invoice,
        charges: (invoice.charges ?? []).filter((c) => c.id !== chargeId),
        totalAmount: invoice.totalAmount - amount,
      });
      addToast(t('charge_removed', { ns: 'billing' }), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('charge_remove_failed', { ns: 'billing' })), 'error');
    } finally {
      setRemovingChargeId(null);
    }
  }, [invoice, onUpdated, addToast, t]);

  // Stable reference passed to every ChargeRow as `onRemove` — ChargeRow itself
  // closes over its own charge id/amount, this wrapper just adapts the async
  // handler above to the sync (id, amount) => void signature ChargeRow expects.
  const handleRemoveCharge = useCallback(
    (chargeId: string, amount: number) => { void handleRemoveChargeAsync(chargeId, amount); },
    [handleRemoveChargeAsync],
  );

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
        {/* Document type toggle */}
        {invoice.status !== 'CANCELLED' && (
          <div className="flex items-center justify-between px-3 py-2 rounded-shape-xs bg-surface-container border border-outline-variant/40">
            <span className="text-xs font-medium text-on-surface-variant uppercase tracking-wide">
              {t(`document_type_${invoice.documentType?.toLowerCase() ?? 'fattura'}`, { ns: 'billing' })}
            </span>
            <button
              type="button"
              onClick={handleToggleDocumentTypeVoid}
              disabled={switchingType}
              className="text-xs font-medium text-primary hover:text-primary/80 disabled:opacity-50 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-sm min-h-10 px-2"
            >
              {invoice.documentType === 'FATTURA'
                ? t('switch_to_ricevuta', { ns: 'billing' })
                : t('switch_to_fattura', { ns: 'billing' })}
            </button>
          </div>
        )}
        {/* SDI status + XML download (FATTURA only, non-CANCELLED) */}
        {invoice.status !== 'CANCELLED' && invoice.documentType === 'FATTURA' && (
          <div className="flex items-center justify-between px-3 py-2 rounded-shape-xs bg-surface-container border border-outline-variant/40">
            <div className="flex items-center gap-2">
              <span className="text-xs font-medium text-on-surface-variant uppercase tracking-wide">
                {t('sdi_status_label', { ns: 'billing' })}
              </span>
              <M3StatusChip
                label={t(`sdi_status_${invoice.sdiStatus.toLowerCase()}`, { ns: 'billing' })}
                tone={sdiStatusTone(invoice.sdiStatus)}
              />
            </div>
            <button
              type="button"
              onClick={handleDownloadFatturaPAXmlVoid}
              disabled={validatingXml}
              className="flex items-center gap-1 text-xs font-medium text-primary hover:text-primary/80 disabled:opacity-50 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-sm min-h-10 px-2"
            >
              <MaterialIcon name="download" size={18} />
              {t('download_fattura_pa', { ns: 'billing' })}
            </button>
          </div>
        )}
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

        {/* Charges (F&B, room, tourist tax, manual extras) */}
        {(invoice.charges && invoice.charges.length > 0) || (invoice.stayId && invoice.status === 'ISSUED') ? (
          <section aria-labelledby="charges-heading">
            <div className="flex items-center justify-between mb-2">
              <h3 id="charges-heading" className="text-xs font-medium text-on-surface-variant uppercase tracking-wide">
                {t('charges', { ns: 'billing' })}
              </h3>
              {invoice.stayId && invoice.status === 'ISSUED' && (
                <button
                  type="button"
                  onClick={handleOpenAddCharge}
                  className="flex items-center gap-1 text-xs font-medium text-primary hover:text-primary/80 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-sm min-h-10 px-2"
                >
                  <MaterialIcon name="add_circle" size={18} />
                  {t('add_charge_button', { ns: 'billing' })}
                </button>
              )}
            </div>
            {invoice.charges && invoice.charges.length > 0 ? (
              <ul className="divide-y divide-outline-variant">
                {invoice.charges.map((charge) => (
                  <ChargeRow
                    key={charge.id}
                    charge={charge}
                    removable={invoice.status === 'ISSUED' && charge.type === 'EXTRA'}
                    removing={removingChargeId === charge.id}
                    formatCurrency={formatCurrency}
                    onRemove={handleRemoveCharge}
                  />
                ))}
              </ul>
            ) : (
              <p className="text-on-surface-variant italic">{t('no_charges_yet', { ns: 'billing' })}</p>
            )}
          </section>
        ) : null}

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
                      <MaterialIcon
                        name={methodIcon[p.paymentMethod] ?? 'payments'}
                        size={18}
                        className="text-on-surface-variant shrink-0"
                      />
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
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-primary text-on-primary text-sm font-medium hover:opacity-90 focus-visible:outline-solid focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary min-h-10"
        >
          <MaterialIcon name="download" size={18} />
          {t('download_pdf', { ns: 'billing' })}
        </button>
      </div>

      {addingCharge && invoice.stayId && (
        <AddChargeModal
          invoice={invoice}
          stayId={invoice.stayId}
          onClose={handleCloseAddCharge}
          onAdded={handleChargeAdded}
        />
      )}
    </M3Dialog>
  );
});

InvoiceDetailModal.displayName = 'InvoiceDetailModal';
