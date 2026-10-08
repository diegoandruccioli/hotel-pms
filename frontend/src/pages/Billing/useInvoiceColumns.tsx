import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import type { ColumnDef } from '@tanstack/react-table';
import type { InvoiceResponse, InvoiceSearchResult } from '../../types';
import { M3StatusChip } from '../../components/m3';
import { useFormatters } from '../../hooks';
import { invoiceStatusTone } from '../../utils';
import { InvoiceActionsCell } from './InvoiceActionsCell';

interface InvoiceColumnsOptions {
  onView: (inv: InvoiceResponse) => void;
  onPay: (inv: InvoiceResponse) => void;
}

export function useInvoiceColumns({ onView, onPay }: InvoiceColumnsOptions) {
  const { t } = useTranslation('common');
  const { formatCurrency, formatDate } = useFormatters();
  const tView = t('view');
  const tRegisterPayment = t('register_payment');
  const tPending = t('pending');

  return useMemo<ColumnDef<InvoiceSearchResult>[]>(() => [
    {
      id: 'invoiceNumber',
      enableSorting: false,
      header: t('invoice_number'),
      cell: ({ row }) => (
        <span className="font-medium">
          {row.original.invoice.invoiceNumber || (
            <span className="text-on-surface-variant italic">{tPending}</span>
          )}
        </span>
      ),
    },
    {
      id: 'guestName',
      enableSorting: false,
      header: t('guest_name'),
      cell: ({ row }) => <span className="text-on-surface-variant">{row.original.guestName ?? '—'}</span>,
    },
    {
      id: 'issueDate',
      accessorFn: (r) => r.invoice.issueDate,
      header: t('issue_date'),
      cell: ({ row }) => (
        <span className="text-on-surface-variant">{formatDate(row.original.invoice.issueDate)}</span>
      ),
    },
    {
      id: 'totalAmount',
      accessorFn: (r) => r.invoice.totalAmount,
      header: t('total_amount'),
      cell: ({ row }) => <span className="font-medium">{formatCurrency(row.original.invoice.totalAmount)}</span>,
    },
    {
      id: 'status',
      accessorFn: (r) => r.invoice.status,
      header: t('status'),
      cell: ({ row }) => (
        <M3StatusChip
          label={t(`invoice_status_${row.original.invoice.status}`, row.original.invoice.status)}
          tone={invoiceStatusTone[row.original.invoice.status]}
        />
      ),
    },
    {
      id: 'actions',
      enableSorting: false,
      header: () => <span className="sr-only">{t('actions')}</span>,
      cell: ({ row }) => (
        <InvoiceActionsCell
          invoice={row.original.invoice}
          onView={onView}
          onPay={onPay}
          tView={tView}
          tRegisterPayment={tRegisterPayment}
        />
      ),
    },
  ], [t, tPending, tView, tRegisterPayment, formatDate, formatCurrency, onView, onPay]);
}
