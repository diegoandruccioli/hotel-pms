import { useCallback, memo } from 'react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
import type { InvoiceResponse, PaymentMethod, ChargeType } from '../../types';

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

interface InfoStripProps {
  /** Left side: a label, optionally followed by a status chip. */
  children: ReactNode;
  /** Right side: the strip's action. */
  action: ReactNode;
}

/** Tinted row of the invoice dialog: a label on the left, one action on the right. */
export const InfoStrip = ({ children, action }: InfoStripProps) => (
  <div className="flex items-center justify-between px-3 py-2 rounded-shape-xs bg-surface-container border border-outline-variant/40">
    <div className="flex items-center gap-2">{children}</div>
    {action}
  </div>
);

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
export const ChargeRow = memo(({ charge, removable, removing, formatCurrency, onRemove }: ChargeRowProps) => {
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

interface PaymentRowProps {
  payment: InvoiceResponse['payments'][number];
  formatCurrency: (val: number) => string;
  formatDateTime: (iso: string) => string;
}

export const PaymentRow = memo(({ payment, formatCurrency, formatDateTime }: PaymentRowProps) => {
  const { t } = useTranslation('billing');
  return (
    <li className="flex items-center justify-between py-2">
      <div className="flex items-center gap-2 min-w-0">
        <MaterialIcon
          name={methodIcon[payment.paymentMethod] ?? 'payments'}
          size={18}
          className="text-on-surface-variant shrink-0"
        />
        <div className="min-w-0">
          <p className="text-on-surface">
            {t(`payment_method_${payment.paymentMethod.toLowerCase()}`)}
          </p>
          <p className="text-xs text-on-surface-variant">
            {formatDateTime(payment.paymentDate)}
            {payment.transactionReference && ` · ${payment.transactionReference}`}
          </p>
        </div>
      </div>
      <span className="font-medium text-tertiary shrink-0 ml-4">
        {formatCurrency(payment.amount)}
      </span>
    </li>
  );
});

PaymentRow.displayName = 'PaymentRow';
