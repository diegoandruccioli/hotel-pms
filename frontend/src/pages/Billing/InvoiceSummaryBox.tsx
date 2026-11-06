import { memo } from 'react';
import { useTranslation } from 'react-i18next';
import { useFormatters } from '../../hooks';
import type { InvoiceResponse } from '../../types';

interface InvoiceSummaryBoxProps {
  invoice: Pick<InvoiceResponse, 'invoiceNumber' | 'totalAmount'>;
}

/** Invoice number and total, shown at the top of the payment and add-charge dialogs. */
export const InvoiceSummaryBox = memo(({ invoice }: InvoiceSummaryBoxProps) => {
  const { t } = useTranslation('common');
  const { formatCurrency } = useFormatters();
  return (
    <div className="rounded-shape-sm bg-surface-container px-4 py-3 text-sm font-body space-y-1">
      <p className="text-on-surface-variant">
        {t('invoice_number')}{' '}
        <span className="font-medium text-on-surface">{invoice.invoiceNumber}</span>
      </p>
      <p className="text-on-surface-variant">
        {t('total_amount')}{' '}
        <span className="font-medium text-on-surface">{formatCurrency(invoice.totalAmount)}</span>
      </p>
    </div>
  );
});

InvoiceSummaryBox.displayName = 'InvoiceSummaryBox';
