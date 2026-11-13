import { useCallback, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import type { InvoiceResponse } from '../../types';
import { M3RowActionsMenu, M3TableActionLink, type RowAction } from '../../components/m3';

interface ActionsCellProps {
  invoice: InvoiceResponse;
  onView: (inv: InvoiceResponse) => void;
  onPay: (inv: InvoiceResponse) => void;
  tView: string;
  tRegisterPayment: string;
}

/**
 * An issued invoice has two actions. From `sm` up they sit in the row as text; on a phone the pinned
 * last column would be about 250 px wide and cover number, guest, amount and status, so there they
 * live in one row menu (44 px).
 */
export const InvoiceActionsCell = ({ invoice, onView, onPay, tView, tRegisterPayment }: ActionsCellProps) => {
  const { t } = useTranslation('common');
  const handleView = useCallback(() => onView(invoice), [onView, invoice]);
  const handlePay  = useCallback(() => onPay(invoice),  [onPay,  invoice]);
  const payable = invoice.status !== 'PAID' && invoice.status !== 'CANCELLED';

  const items = useMemo<RowAction[]>(() => [
    { id: 'view', label: tView, icon: 'visibility', onSelect: handleView },
    { id: 'pay', label: tRegisterPayment, icon: 'payments', onSelect: handlePay },
  ], [tView, tRegisterPayment, handleView, handlePay]);

  // A paid or cancelled invoice has the one action: it stays an inline link at every width.
  if (!payable) {
    return (
      <div className="flex items-center justify-end">
        <M3TableActionLink onClick={handleView}>{tView}</M3TableActionLink>
      </div>
    );
  }

  return (
    <>
      <div className="hidden items-center justify-end gap-1 sm:flex">
        <M3TableActionLink onClick={handleView}>{tView}</M3TableActionLink>
        <M3TableActionLink tone="tertiary" onClick={handlePay}>{tRegisterPayment}</M3TableActionLink>
      </div>
      <div className="flex justify-end sm:hidden">
        <M3RowActionsMenu
          label={t('invoice_actions_for', { number: invoice.invoiceNumber || t('pending') })}
          items={items}
        />
      </div>
    </>
  );
};
