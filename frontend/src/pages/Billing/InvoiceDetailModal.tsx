import { useFormatters } from '../../hooks';
import { useCallback, memo, useState } from 'react';
import { billingService } from '../../services';
import { useToastStore } from '../../store';
import { useTranslation } from 'react-i18next';
import { M3Button } from '../../components/m3';
import { M3ConfirmDialog } from '../../components/m3';
import { M3Dialog } from '../../components/m3';
import { M3StatusChip } from '../../components/m3';
import { M3TableActionLink } from '../../components/m3';
import { MaterialIcon } from '../../components/MaterialIcon';
import { AddChargeModal } from './AddChargeModal';
import { ChargeRow, InfoStrip, PaymentRow } from './InvoiceDetailRows';
import { getErrorMessage, invoiceStatusTone, sdiStatusTone } from '../../utils';
import type { BillingDocumentType as DocumentType, InvoiceResponse } from '../../types';

interface ChargeToRemove {
  id: string;
  amount: number;
}

interface Props {
  invoice: InvoiceResponse;
  onClose: () => void;
  onUpdated?: (updated: InvoiceResponse) => void;
}

export const InvoiceDetailModal = memo(({ invoice, onClose, onUpdated }: Props) => {
  const { t } = useTranslation(['billing', 'common']);
  const addToast = useToastStore((s) => s.addToast);
  const [switchingType, setSwitchingType] = useState(false);
  const [validatingXml, setValidatingXml] = useState(false);
  const [addingCharge, setAddingCharge] = useState(false);
  const [removingChargeId, setRemovingChargeId] = useState<string | null>(null);
  const [chargeToRemove, setChargeToRemove] = useState<ChargeToRemove | null>(null);

  const handleDownloadPdf = useCallback(async () => {
    try {
      await billingService.downloadPdf(invoice.id);
    } catch (err: unknown) {
      const e = err as { response?: { data?: { detail?: string } }; message?: string };
      addToast(e.response?.data?.detail ?? e.message ?? t('download_failed', { ns: 'common' }), 'error');
    }
  }, [invoice.id, addToast, t]);

  // The actual download (below) fires via a hidden iframe (see billingService), which has
  // no way to observe an HTTP error response — a legitimate rejection (e.g. incomplete
  // guest address) would otherwise fail completely silently. Validate first, over a real
  // XHR that can surface the error as a toast, and only trigger the iframe on success.
  const handleDownloadFatturaPAXml = useCallback(async () => {
    setValidatingXml(true);
    try {
      await billingService.validateFatturaPAXml(invoice.id);
      await billingService.downloadFatturaPAXml(invoice.id);
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
  // closes over its own charge id/amount; this only opens the confirmation.
  const handleRemoveCharge = useCallback(
    (chargeId: string, amount: number) => setChargeToRemove({ id: chargeId, amount }),
    [],
  );

  const handleCancelRemoveCharge = useCallback(() => setChargeToRemove(null), []);
  const handleConfirmRemoveCharge = useCallback(() => {
    if (chargeToRemove) {
      void handleRemoveChargeAsync(chargeToRemove.id, chargeToRemove.amount);
    }
    setChargeToRemove(null);
  }, [chargeToRemove, handleRemoveChargeAsync]);

  // Escape (useEscapeKey) is a document-level listener on every open dialog, so it would
  // also close this one underneath a nested dialog: ignore the parent's close meanwhile.
  const nestedDialogOpen = chargeToRemove !== null || addingCharge;
  const handleDialogClose = useCallback(() => {
    if (!nestedDialogOpen) onClose();
  }, [nestedDialogOpen, onClose]);

  const { formatCurrency, formatDateTime } = useFormatters();

  const totalPaid = invoice.payments.reduce((sum, p) => sum + p.amount, 0);

  return (
    <M3Dialog
      open
      title={t('invoice_detail_title', { ns: 'billing' })}
      titleId="invoice-detail-title"
      onClose={handleDialogClose}
    >
      <div className="space-y-6 text-sm font-body">
        {/* Document type toggle */}
        {invoice.status !== 'CANCELLED' && (
          <InfoStrip
            action={
              <M3TableActionLink onClick={handleToggleDocumentTypeVoid} disabled={switchingType} className="text-xs">
                {invoice.documentType === 'FATTURA'
                  ? t('switch_to_ricevuta', { ns: 'billing' })
                  : t('switch_to_fattura', { ns: 'billing' })}
              </M3TableActionLink>
            }
          >
            <span className="text-xs font-medium text-on-surface-variant uppercase tracking-wide">
              {t(`document_type_${invoice.documentType?.toLowerCase() ?? 'fattura'}`, { ns: 'billing' })}
            </span>
          </InfoStrip>
        )}
        {/* SDI status + XML download (FATTURA only, non-CANCELLED) */}
        {invoice.status !== 'CANCELLED' && invoice.documentType === 'FATTURA' && (
          <InfoStrip
            action={
              <M3TableActionLink onClick={handleDownloadFatturaPAXmlVoid} disabled={validatingXml} className="gap-1 text-xs">
                <MaterialIcon name="download" size={18} />
                {t('download_fattura_pa', { ns: 'billing' })}
              </M3TableActionLink>
            }
          >
            <span className="text-xs font-medium text-on-surface-variant uppercase tracking-wide">
              {t('sdi_status_label', { ns: 'billing' })}
            </span>
            <M3StatusChip
              label={t(`sdi_status_${invoice.sdiStatus.toLowerCase()}`, { ns: 'billing' })}
              tone={sdiStatusTone[invoice.sdiStatus]}
            />
          </InfoStrip>
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
                tone={invoiceStatusTone[invoice.status]}
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
                <M3TableActionLink onClick={handleOpenAddCharge} className="gap-1 text-xs">
                  <MaterialIcon name="add_circle" size={18} />
                  {t('add_charge_button', { ns: 'billing' })}
                </M3TableActionLink>
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
                  <PaymentRow key={p.id} payment={p} formatCurrency={formatCurrency} formatDateTime={formatDateTime} />
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
        <M3Button type="button" icon="download" onClick={handleDownloadPdf}>
          {t('download_pdf', { ns: 'billing' })}
        </M3Button>
      </div>

      {chargeToRemove && (
        <M3ConfirmDialog
          title={t('remove_charge', { ns: 'billing' })}
          message={t('confirm_remove_charge', { ns: 'billing' })}
          onConfirm={handleConfirmRemoveCharge}
          onCancel={handleCancelRemoveCharge}
        />
      )}

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
