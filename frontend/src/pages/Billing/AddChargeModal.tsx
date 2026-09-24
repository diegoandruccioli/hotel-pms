import { useState, useCallback, useMemo, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { M3Dialog } from '../../components/m3';
import { M3Button } from '../../components/m3';
import { M3TextField } from '../../components/m3';
import { M3Select } from '../../components/m3';
import { billingService } from '../../services';
import { useToastStore } from '../../store';
import { getErrorMessage } from '../../utils';
import type { InvoiceResponse } from '../../types';

interface Props {
  invoice: InvoiceResponse;
  stayId: string;
  onClose: () => void;
  onAdded: (updated: InvoiceResponse) => void;
}

/** Preset descriptions for the most common manual extras — always posted as
 * ChargeType EXTRA; "OTHER" swaps the select for a free-text field instead. */
const PRESETS = ['MINIBAR', 'LAUNDRY', 'PARKING', 'LATE_CHECKOUT', 'OTHER'] as const;
type Preset = (typeof PRESETS)[number];

export const AddChargeModal = memo(({ invoice, stayId, onClose, onAdded }: Props) => {
  const { t, i18n } = useTranslation(['billing', 'common']);
  const addToast = useToastStore((s) => s.addToast);

  const [preset, setPreset] = useState<Preset>('MINIBAR');
  const [customDescription, setCustomDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [loading, setLoading] = useState(false);
  const [amountError, setAmountError] = useState('');
  const [descriptionError, setDescriptionError] = useState('');

  const formatCurrency = useCallback(
    (val: number) =>
      new Intl.NumberFormat(i18n.language, {
        style: 'currency',
        currency: 'EUR',
      }).format(val),
    [i18n.language],
  );

  const handlePresetChange = useCallback(
    (e: React.ChangeEvent<HTMLSelectElement>) => setPreset(e.target.value as Preset),
    [],
  );

  const handleCustomDescriptionChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setCustomDescription(e.target.value);
      setDescriptionError('');
    },
    [],
  );

  const handleAmountChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setAmount(e.target.value);
    setAmountError('');
  }, []);

  const presetOptions = useMemo(
    () => PRESETS.map((p) => ({
      value: p,
      label: t(`charge_preset_${p.toLowerCase()}`, { ns: 'billing' }),
    })),
    [t],
  );

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();

      const description = preset === 'OTHER'
        ? customDescription.trim()
        : t(`charge_preset_${preset.toLowerCase()}`, { ns: 'billing' });
      if (preset === 'OTHER' && !description) {
        setDescriptionError(t('error_charge_description_required', { ns: 'billing' }));
        return;
      }

      const parsed = parseFloat(amount.replace(',', '.'));
      if (isNaN(parsed) || parsed <= 0) {
        setAmountError(t('error_invalid_amount', { ns: 'billing' }));
        return;
      }

      setLoading(true);
      try {
        const charge = await billingService.addCharge(stayId, {
          type: 'EXTRA',
          description,
          amount: parsed,
        });
        onAdded({
          ...invoice,
          charges: [...(invoice.charges ?? []), charge],
          totalAmount: invoice.totalAmount + charge.amount,
        });
        addToast(t('charge_added', { ns: 'billing' }), 'success');
        onClose();
      } catch (err: unknown) {
        addToast(getErrorMessage(err, t('charge_add_failed', { ns: 'billing' })), 'error');
      } finally {
        setLoading(false);
      }
    },
    [preset, customDescription, amount, stayId, invoice, onAdded, onClose, addToast, t],
  );

  return (
    <M3Dialog
      open
      title={t('add_charge_title', { ns: 'billing' })}
      titleId="add-charge-modal-title"
      onClose={onClose}
    >
      <form onSubmit={handleSubmit} className="space-y-5" noValidate>
        {/* Invoice summary */}
        <div className="rounded-shape-sm bg-surface-container px-4 py-3 text-sm font-body space-y-1">
          <p className="text-on-surface-variant">
            {t('invoice_number', { ns: 'common' })}{' '}
            <span className="font-medium text-on-surface">{invoice.invoiceNumber}</span>
          </p>
          <p className="text-on-surface-variant">
            {t('total_amount', { ns: 'common' })}{' '}
            <span className="font-medium text-on-surface">
              {formatCurrency(invoice.totalAmount)}
            </span>
          </p>
        </div>

        {/* Charge description preset */}
        <M3Select
          label={t('charge_description', { ns: 'billing' })}
          required
          options={presetOptions}
          value={preset}
          onChange={handlePresetChange}
        />

        {preset === 'OTHER' && (
          <M3TextField
            label={`${t('charge_description_custom', { ns: 'billing' })} *`}
            type="text"
            value={customDescription}
            onChange={handleCustomDescriptionChange}
            required
            errorText={descriptionError || undefined}
          />
        )}

        {/* Amount */}
        <M3TextField
          label={`${t('payment_amount', { ns: 'billing' })} *`}
          type="number"
          min="0.01"
          step="0.01"
          value={amount}
          onChange={handleAmountChange}
          required
          errorText={amountError || undefined}
        />

        {/* Actions */}
        <div className="flex justify-end gap-3 pt-2">
          <M3Button
            type="button"
            variant="outlined"
            onClick={onClose}
            disabled={loading}
          >
            {t('cancel', { ns: 'common' })}
          </M3Button>
          <M3Button type="submit" loading={loading} icon="add_circle">
            {t('confirm_add_charge', { ns: 'billing' })}
          </M3Button>
        </div>
      </form>
    </M3Dialog>
  );
});

AddChargeModal.displayName = 'AddChargeModal';
