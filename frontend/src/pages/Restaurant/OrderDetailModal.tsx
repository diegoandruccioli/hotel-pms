import { memo, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { M3Dialog } from '../../components/m3/M3Dialog';
import { M3Button } from '../../components/m3/M3Button';
import { M3StatusChip } from '../../components/m3/M3StatusChip';
import type { RestaurantOrderResponse, OrderStatus } from '../../types/fb.types';

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
          <div className="overflow-x-auto">
            <table className="w-full text-sm font-body">
              <thead>
                <tr className="border-b border-outline-variant text-on-surface-variant text-left">
                  <th scope="col" className="pb-2 pr-4 font-medium">{t('item_name')}</th>
                  <th scope="col" className="pb-2 pr-4 text-right font-medium">{t('quantity')}</th>
                  <th scope="col" className="pb-2 pr-4 text-right font-medium">{t('unit_price')}</th>
                  <th scope="col" className="pb-2 text-right font-medium">{t('subtotal')}</th>
                </tr>
              </thead>
              <tbody>
                {order.items.map((item) => (
                  <tr key={item.id} className="border-b border-outline-variant/50">
                    <td className="py-2 pr-4 text-on-surface">{item.itemName}</td>
                    <td className="py-2 pr-4 text-right text-on-surface-variant">{item.quantity}</td>
                    <td className="py-2 pr-4 text-right text-on-surface-variant">
                      {formatCurrency(item.unitPrice)}
                    </td>
                    <td className="py-2 text-right font-medium text-on-surface">
                      {formatCurrency(item.unitPrice * item.quantity)}
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={3} className="pt-3 pr-4 text-right font-medium text-on-surface">
                    {t('total_amount')}
                  </td>
                  <td className="pt-3 text-right font-bold text-on-surface">
                    {formatCurrency(order.totalAmount)}
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>
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
