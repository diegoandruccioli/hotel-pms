import { memo, useCallback, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { M3Dialog } from '../../components/m3/M3Dialog';
import { M3Button } from '../../components/m3/M3Button';
import { M3StatusChip } from '../../components/m3/M3StatusChip';
import { M3Table, M3TableRow, M3TableCell } from '../../components/m3/M3Table';
import type { RestaurantOrderResponse, OrderStatus } from '../../types';

interface Props {
  order: RestaurantOrderResponse;
  onClose: () => void;
}

const getStatusTone = (status: OrderStatus | string) => {
  switch (status) {
    case 'PENDING': return 'warning' as const;
    case 'PREPARED': return 'info' as const;
    case 'DELIVERED': return 'neutral' as const;
    case 'BILLED_TO_ROOM': return 'info' as const;
    default: return 'neutral' as const;
  }
};

export const OrderDetailModal = memo(({ order, onClose }: Props) => {
  const { t, i18n } = useTranslation('common');

  const formatCurrency = useCallback(
    (val: number) =>
      new Intl.NumberFormat(i18n.language, { style: 'currency', currency: 'EUR' }).format(val),
    [i18n.language],
  );

  const formatDate = useCallback(
    (dateStr?: string) => {
      if (!dateStr) return '-';
      return new Date(dateStr).toLocaleString(i18n.language);
    },
    [i18n.language],
  );

  const itemHeaders = useMemo(() => [
    t('item_name'),
    t('quantity'),
    t('unit_price'),
    t('subtotal'),
  ], [t]);

  return (
    <M3Dialog
      open
      title={t('order_detail_title')}
      titleId="order-detail-modal-title"
      onClose={onClose}
    >
      <div className="space-y-4">
        <div className="rounded-shape-sm bg-surface-container px-4 py-3 space-y-2 text-sm font-body">
          <div className="flex justify-between items-center">
            <span className="text-on-surface-variant">{t('order_id')}</span>
            <span className="font-medium text-on-surface font-mono" title={order.id}>
              {order.id.substring(0, 8)}...
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-on-surface-variant">{t('stay_id')}</span>
            <span className="font-medium text-on-surface font-mono" title={order.stayId}>
              {order.stayId.substring(0, 8)}...
            </span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-on-surface-variant">{t('date')}</span>
            <span className="font-medium text-on-surface">{formatDate(order.orderDate)}</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-on-surface-variant">{t('status')}</span>
            <M3StatusChip
              label={order.status.replace('_', ' ')}
              tone={getStatusTone(order.status)}
            />
          </div>
        </div>

        {order.items && order.items.length > 0 && (
          <M3Table headers={itemHeaders}>
            {order.items.map((item) => (
              <M3TableRow key={item.id}>
                <M3TableCell>{item.itemName}</M3TableCell>
                <M3TableCell className="text-right text-on-surface-variant">{item.quantity}</M3TableCell>
                <M3TableCell className="text-right text-on-surface-variant">
                  {formatCurrency(item.unitPrice)}
                </M3TableCell>
                <M3TableCell className="text-right font-medium">
                  {formatCurrency(item.unitPrice * item.quantity)}
                </M3TableCell>
              </M3TableRow>
            ))}
            <M3TableRow className="hover:bg-transparent">
              <M3TableCell colSpan={3} className="text-right font-medium">
                {t('total_amount')}
              </M3TableCell>
              <M3TableCell className="text-right font-bold">
                {formatCurrency(order.totalAmount)}
              </M3TableCell>
            </M3TableRow>
          </M3Table>
        )}

        <div className="flex justify-end pt-2">
          <M3Button type="button" variant="outlined" onClick={onClose}>
            {t('close')}
          </M3Button>
        </div>
      </div>
    </M3Dialog>
  );
});

OrderDetailModal.displayName = 'OrderDetailModal';
