import { useCallback } from 'react';
import type { InvoiceResponse } from '../../types';
import { M3TableActionLink } from '../../components/m3';

interface ActionsCellProps {
  invoice: InvoiceResponse;
  onView: (inv: InvoiceResponse) => void;
  onPay: (inv: InvoiceResponse) => void;
  tView: string;
  tRegisterPayment: string;
}

export const InvoiceActionsCell = ({ invoice, onView, onPay, tView, tRegisterPayment }: ActionsCellProps) => {
  const handleView = useCallback(() => onView(invoice), [onView, invoice]);
  const handlePay  = useCallback(() => onPay(invoice),  [onPay,  invoice]);

  return (
    <div className="flex items-center justify-end gap-1">
      <M3TableActionLink onClick={handleView}>{tView}</M3TableActionLink>
      {invoice.status !== 'PAID' && invoice.status !== 'CANCELLED' && (
        <M3TableActionLink tone="tertiary" onClick={handlePay}>{tRegisterPayment}</M3TableActionLink>
      )}
    </div>
  );
};
