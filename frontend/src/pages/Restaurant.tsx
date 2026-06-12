import { useState, useEffect, useCallback, memo, useMemo } from 'react';
import { fbService } from '../services/fbService';
import type { MenuItemResponse, RestaurantOrderResponse, OrderStatus } from '../types/fb.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3Table, M3TableRow, M3TableCell } from '../components/m3/M3Table';
import { M3StatusChip } from '../components/m3/M3StatusChip';
import { M3Card } from '../components/m3/M3Card';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useAuthStore } from '../store/authStore';
import { useToastStore } from '../store/toastStore';
import { OrderFormModal } from './Restaurant/OrderFormModal';
import { OrderDetailModal } from './Restaurant/OrderDetailModal';
import { MenuFormModal } from './Restaurant/MenuFormModal';

const CONFIRMABLE_STATUSES = new Set<string>(['PENDING', 'PREPARED']);

interface MenuItemRowProps {
  mi: MenuItemResponse;
  deletingMenuId: string | null;
  onEdit: (mi: MenuItemResponse) => void;
  onDelete: (mi: MenuItemResponse) => void;
  formatCurrencyFn: (price: number) => string;
  tLabel: (key: string) => string;
  tCommon: (key: string) => string;
}

const MenuItemRow = memo(({
  mi, deletingMenuId, onEdit, onDelete, formatCurrencyFn, tLabel, tCommon,
}: MenuItemRowProps) => {
  const handleEdit = useCallback(() => onEdit(mi), [onEdit, mi]);
  const handleDelete = useCallback(() => onDelete(mi), [onDelete, mi]);
  return (
    <tr key={mi.id} className="hover:bg-surface-variant/30">
      <td className="px-3 py-2 font-medium">{mi.name}</td>
      <td className="px-3 py-2 text-on-surface-variant">{mi.category}</td>
      <td className="px-3 py-2 text-right">{formatCurrencyFn(mi.price)}</td>
      <td className="px-3 py-2 text-center">
        <M3StatusChip
          label={mi.available ? tLabel('menu_available_yes') : tLabel('menu_available_no')}
          tone={mi.available ? 'success' : 'neutral'}
        />
      </td>
      <td className="px-3 py-2 text-right">
        <div className="flex justify-end gap-2">
          <button type="button" onClick={handleEdit}
            className="text-primary hover:text-primary/80 text-xs font-medium focus:outline-none focus:ring-2 focus:ring-primary rounded"
            aria-label={`${tLabel('menu_edit_item')} ${mi.name}`}>
            {tCommon('edit')}
          </button>
          <button type="button" onClick={handleDelete}
            disabled={deletingMenuId === mi.id}
            className="text-error hover:text-error/80 text-xs font-medium disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-error rounded"
            aria-label={`${tLabel('menu_delete_item')} ${mi.name}`}>
            {tCommon('delete')}
          </button>
        </div>
      </td>
    </tr>
  );
});
MenuItemRow.displayName = 'MenuItemRow';

const getStatusTone = (status: OrderStatus | string) => {
  switch (status) {
    case 'PENDING': return 'warning' as const;
    case 'PREPARING': return 'info' as const;
    case 'PREPARED': return 'info' as const;
    case 'READY': return 'success' as const;
    case 'DELIVERED': return 'neutral' as const;
    case 'CANCELLED': return 'error' as const;
    case 'BILLED_TO_ROOM': return 'info' as const;
    default: return 'neutral' as const;
  }
};

interface OrderRowProps {
  order: RestaurantOrderResponse;
  confirmingId: string | null;
  onConfirm: (id: string) => void;
  onView: (order: RestaurantOrderResponse) => void;
  formatCurrency: (amount: number) => string;
  formatDate: (dateStr?: string) => string;
  t: TFunction<'common'>;
}

const OrderRow = memo(({ order, confirmingId, onConfirm, onView, formatCurrency, formatDate, t }: OrderRowProps) => {
  const isConfirmable = CONFIRMABLE_STATUSES.has(order.status);
  const isConfirming = confirmingId === order.id;
  const handleConfirmClick = useCallback(() => onConfirm(order.id), [onConfirm, order.id]);
  const handleViewClick = useCallback(() => onView(order), [onView, order]);

  return (
    <M3TableRow key={order.id}>
      <M3TableCell className="font-medium">
        <span className="truncate block max-w-[120px]" title={order.id}>{order.id.substring(0, 8)}...</span>
      </M3TableCell>
      <M3TableCell className="text-on-surface-variant">
        <span className="truncate block max-w-[120px]" title={order.stayId}>{order.stayId.substring(0, 8)}...</span>
      </M3TableCell>
      <M3TableCell className="text-on-surface-variant">{formatDate(order.orderDate)}</M3TableCell>
      <M3TableCell className="font-medium">{formatCurrency(order.totalAmount)}</M3TableCell>
      <M3TableCell>
        <M3StatusChip label={t(`order_status_${order.status}`, order.status.replace(/_/g, ' '))} tone={getStatusTone(order.status)} />
      </M3TableCell>
      <M3TableCell className="text-right">
        <div className="flex justify-end gap-2">
          {isConfirmable && (
            <button
              type="button"
              onClick={handleConfirmClick}
              disabled={isConfirming}
              aria-label={`${t('confirm_order')} ${order.id}`}
              className="text-primary hover:text-primary/80 font-medium text-sm disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-1"
            >
              {isConfirming
                ? <MaterialIcon name="progress_activity" size={14} className="animate-spin" aria-hidden="true" />
                : t('confirm')}
            </button>
          )}
          <button
            type="button"
            onClick={handleViewClick}
            aria-label={`${t('view')} ${order.id}`}
            className="text-primary hover:text-primary/80 font-medium text-sm"
          >
            {t('view')}
          </button>
        </div>
      </M3TableCell>
    </M3TableRow>
  );
});

export const Restaurant = memo(() => {
  const { t, i18n } = useTranslation('common');
  const { t: tMenu } = useTranslation('restaurant');
  const role = useAuthStore((s) => s.user?.role);
  const { addToast } = useToastStore();
  const isAdminOrOwner = role === 'ADMIN' || role === 'OWNER';

  const [orders, setOrders] = useState<RestaurantOrderResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const [isOrderModalOpen, setIsOrderModalOpen] = useState(false);
  const [selectedOrder, setSelectedOrder] = useState<RestaurantOrderResponse | null>(null);

  const [menuItems, setMenuItems] = useState<MenuItemResponse[]>([]);
  const [menuFormTarget, setMenuFormTarget] = useState<MenuItemResponse | 'new' | null>(null);
  const [deletingMenuId, setDeletingMenuId] = useState<string | null>(null);

  const loadOrders = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await fbService.getAllOrders();
      setOrders(data);
    } catch (err: unknown) {
      const e = err as {response?: {data?: {detail?: string}}, message?: string};
      setError(e.response?.data?.detail || e.message || t('failed_load_orders'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  const handleConfirm = useCallback(async (orderId: string) => {
    setConfirmingId(orderId);
    try {
      await fbService.confirmOrder(orderId);
      await loadOrders();
    } catch (err: unknown) {
      const e = err as {response?: {data?: {detail?: string}}, message?: string};
      setError(e.response?.data?.detail || e.message || t('confirm_order_failed'));
    } finally {
      setConfirmingId(null);
    }
  }, [loadOrders, t]);

  const loadMenu = useCallback(async () => {
    if (!isAdminOrOwner) return;
    try {
      const items = await fbService.getMenuItems();
      setMenuItems(items);
    } catch { /* non-blocking */ }
  }, [isAdminOrOwner]);

  useEffect(() => { loadMenu(); }, [loadMenu]);

  const handleMenuSaved = useCallback(async () => {
    setMenuFormTarget(null);
    await loadMenu();
  }, [loadMenu]);

  const handleMenuEdit = useCallback((mi: MenuItemResponse) => setMenuFormTarget(mi), []);
  const handleOpenMenuForm = useCallback(() => setMenuFormTarget('new'), []);
  const handleCloseMenuForm = useCallback(() => setMenuFormTarget(null), []);

  const handleDeleteMenuItem = useCallback(async (item: MenuItemResponse) => {
    const confirmed = window.confirm(tMenu('menu_delete_confirm', { name: item.name }));
    if (!confirmed) return;
    setDeletingMenuId(item.id);
    try {
      await fbService.deleteMenuItem(item.id);
      addToast(tMenu('menu_delete_success'), 'success');
      await loadMenu();
    } catch {
      addToast(tMenu('menu_delete_error'), 'error');
    } finally {
      setDeletingMenuId(null);
    }
  }, [addToast, loadMenu, tMenu]);

  const handleOrderCreated = useCallback(async () => { await loadOrders(); }, [loadOrders]);

  const handleOpenOrderModal = useCallback(() => setIsOrderModalOpen(true), []);
  const handleCloseOrderModal = useCallback(() => setIsOrderModalOpen(false), []);
  const handleViewOrder = useCallback((order: RestaurantOrderResponse) => setSelectedOrder(order), []);
  const handleCloseDetail = useCallback(() => setSelectedOrder(null), []);

  const formatCurrency = useCallback((amount: number) => {
    return new Intl.NumberFormat(i18n.language, { style: 'currency', currency: 'EUR' }).format(amount);
  }, [i18n.language]);

  const formatDate = useCallback((dateStr?: string) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleString(i18n.language);
  }, [i18n.language]);

  const tableHeaders = useMemo(() => [
    t('order_id'),
    t('stay_id'),
    t('date'),
    t('total_amount'),
    t('status'),
    <span key="sr" className="sr-only">{t('actions')}</span>
  ], [t]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center">
            <MaterialIcon name="restaurant" className="mr-2 text-primary" />
            {t('nav_restaurant')}
          </h1>
          <p className="text-sm font-body text-on-surface-variant mt-1">{t('restaurant_subtitle')}</p>
        </div>
        <M3Button icon="add" onClick={handleOpenOrderModal}>{t('new_order')}</M3Button>
      </div>

      {loading ? (
        <div className="flex justify-center items-center h-64 bg-surface rounded-shape-md shadow-elevation-1">
          <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
        </div>
      ) : error ? (
        <div className="flex items-center gap-3 px-4 py-4 rounded-shape-sm bg-error-container text-on-error-container">
          <MaterialIcon name="error" size={20} className="flex-shrink-0" />
          <div>
            <h3 className="text-sm font-medium font-body">{t('error_loading_orders')}</h3>
            <p className="mt-1 text-sm font-body opacity-80">{error}</p>
            <button type="button" onClick={loadOrders} className="mt-2 text-sm font-medium underline hover:no-underline">
              {t('try_again')}
            </button>
          </div>
        </div>
      ) : (
        <M3Table headers={tableHeaders}>
          {orders.length === 0 ? (
            <tr><td colSpan={6} className="py-8 text-center text-sm font-body text-on-surface-variant">{t('no_orders')}</td></tr>
          ) : (
            orders.map((order) => (
              <OrderRow
                key={order.id}
                order={order}
                confirmingId={confirmingId}
                onConfirm={handleConfirm}
                onView={handleViewOrder}
                formatCurrency={formatCurrency}
                formatDate={formatDate}
                t={t}
              />
            ))
          )}
        </M3Table>
      )}

      {isAdminOrOwner && (
        <M3Card variant="outlined" className="p-5 space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <MaterialIcon name="menu_book" size={20} className="text-primary" />
              <h2 className="text-sm font-display font-semibold text-on-surface">{tMenu('menu_title')}</h2>
            </div>
            <M3Button icon="add" variant="tonal" onClick={handleOpenMenuForm}>
              {tMenu('menu_add_item')}
            </M3Button>
          </div>
          {menuItems.length === 0 ? (
            <p className="text-sm text-on-surface-variant text-center py-4">{tMenu('menu_no_items')}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm text-on-surface">
                <thead className="bg-surface-variant text-on-surface-variant uppercase text-xs tracking-wide">
                  <tr>
                    <th className="px-3 py-2 text-left">{tMenu('menu_name')}</th>
                    <th className="px-3 py-2 text-left">{tMenu('menu_category')}</th>
                    <th className="px-3 py-2 text-right">{tMenu('menu_price')}</th>
                    <th className="px-3 py-2 text-center">{tMenu('menu_available')}</th>
                    <th className="px-3 py-2 text-right">{t('actions')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant">
                  {menuItems.map((mi) => (
                    <MenuItemRow
                      key={mi.id}
                      mi={mi}
                      deletingMenuId={deletingMenuId}
                      onEdit={handleMenuEdit}
                      onDelete={handleDeleteMenuItem}
                      formatCurrencyFn={formatCurrency}
                      tLabel={tMenu}
                      tCommon={t}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </M3Card>
      )}

      {isOrderModalOpen && (
        <OrderFormModal onClose={handleCloseOrderModal} onCreated={handleOrderCreated} />
      )}

      {selectedOrder && (
        <OrderDetailModal order={selectedOrder} onClose={handleCloseDetail} />
      )}

      {menuFormTarget && (
        <MenuFormModal
          item={menuFormTarget === 'new' ? undefined : menuFormTarget}
          onClose={handleCloseMenuForm}
          onSaved={handleMenuSaved}
        />
      )}
    </div>
  );
});
