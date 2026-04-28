import { useState, useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { M3Dialog } from '../../components/m3/M3Dialog';
import { M3Button } from '../../components/m3/M3Button';
import { M3TextField } from '../../components/m3/M3TextField';
import { billingService } from '../../services/billingService';
import { useToastStore } from '../../store/toastStore';
import type { InvoiceResponse, PaymentMethod } from '../../types/billing.types';

interface Props {
  invoice: InvoiceResponse;
  onClose: () => void;
  onPaid: (updated: InvoiceResponse) => void;
}

const PAYMENT_METHODS: PaymentMethod[] = [
  'CASH',
  'CREDIT_CARD',
  'DEBIT_CARD',
  'BANK_TRANSFER',
  'CHECK',
];

export const PaymentModal = memo(({ invoice, onClose, onPaid }: Props) => {
  const { t, i18n } = useTranslation(['billing', 'common']);
  const addToast = useToastStore((s) => s.addToast);

  const [amount, setAmount] = useState(String(invoice.totalAmount));
  const [method, setMethod] = useState<PaymentMethod>('CASH');
  const [reference, setReference] = useState('');
  const [loading, setLoading] = useState(false);
  const [amountError, setAmountError] = useState('');

  const formatCurrency = useCallback(
    (val: number) =>
      new Intl.NumberFormat(i18n.language, {
        style: 'currency',
        currency: 'EUR',
      }).format(val),
    [i18n.language],
  );

  const handleAmountChange = useCallback((val: string) => {
    setAmount(val);
    setAmountError('');
  }, []);

  const handleAmountInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => handleAmountChange(e.target.value),
    [handleAmountChange],
  );

  const handleMethodChange = useCallback(
    (e: React.ChangeEvent<HTMLSelectElement>) => setMethod(e.target.value as PaymentMethod),
    [],
  );

  const handleReferenceChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => setReference(e.target.value),
    [],
  );

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      const parsed = parseFloat(amount.replace(',', '.'));
      if (isNaN(parsed) || parsed <= 0) {
        setAmountError(t('error_invalid_amount', { ns: 'billing' }));
        return;
      }
      setLoading(true);
      try {
        const payment = await billingService.processPayment(invoice.id, {
          amount: parsed,
          paymentMethod: method,
          transactionReference: reference.trim() || undefined,
        });
        onPaid({
          ...invoice,
          status: 'PAID',
          payments: [...invoice.payments, payment],
        });
        addToast(t('payment_registered', { ns: 'billing' }), 'success');
        onClose();
      } catch {
        addToast(t('payment_failed', { ns: 'billing' }), 'error');
      } finally {
        setLoading(false);
      }
    },
    [amount, method, reference, invoice, onPaid, onClose, addToast, t],
  );

  return (
    <M3Dialog
      open
      title={t('register_payment_title', { ns: 'billing' })}
      titleId="payment-modal-title"
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

        {/* Amount */}
        <M3TextField
          label={`${t('payment_amount', { ns: 'billing' })} *`}
          type="number"
          min="0.01"
          step="0.01"
          value={amount}
          onChange={handleAmountInputChange}
          required
          errorText={amountError || undefined}
        />

        {/* Payment method */}
        <div>
          <label
            htmlFor="payment-method-select"
            className="block text-sm font-medium font-body text-on-surface mb-1"
          >
            {t('payment_method', { ns: 'billing' })} *
          </label>
          <select
            id="payment-method-select"
            value={method}
            onChange={handleMethodChange}
            required
            className={[
              'w-full rounded-shape-xs border border-outline bg-surface',
              'text-on-surface px-4 py-3 text-sm font-body',
              'focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary focus:ring-offset-1',
              'hover:border-on-surface transition-colors',
            ].join(' ')}
          >
            {PAYMENT_METHODS.map((m) => (
              <option key={m} value={m}>
                {t(`payment_method_${m.toLowerCase()}`, { ns: 'billing' })}
              </option>
            ))}
          </select>
        </div>

        {/* Reference (optional) */}
        <M3TextField
          label={t('transaction_reference', { ns: 'billing' })}
          type="text"
          value={reference}
          onChange={handleReferenceChange}
          supportingText={t('reference_supporting', { ns: 'billing' })}
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
          <M3Button type="submit" loading={loading} icon="payments">
            {t('confirm_payment', { ns: 'billing' })}
          </M3Button>
        </div>
      </form>
    </M3Dialog>
  );
});

PaymentModal.displayName = 'PaymentModal';
