import { useState, useCallback, memo, useMemo } from 'react';
import type { MenuItemResponse, RestaurantOrderResponse, OrderStatus } from '../types/fb.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3Table, M3TableRow, M3TableCell } from '../components/m3/M3Table';
import { M3StatusChip } from '../components/m3/M3StatusChip';
import { M3Card } from '../components/m3/M3Card';
import { M3TableActionLink } from '../components/m3/M3TableActionLink';
import { M3LoadingState } from '../components/m3/M3LoadingState';
import { M3ErrorState } from '../components/m3/M3ErrorState';
import { M3TableEmptyRow } from '../components/m3/M3EmptyState';
import { M3Select } from '../components/m3/M3Select';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useAuthStore } from '../store/authStore';
import { useToastStore } from '../store/toastStore';
import { getErrorMessage } from '../utils/errorMessage';
import { useQueryClient } from '@tanstack/react-query';
import { useOrders, useMenuItems, useConfirmOrder, useDeleteMenuItem } from '../hooks/queries/useFb';
import { queryKeys } from '../lib/queryKeys';
import { OrderFormModal } from './Restaurant/OrderFormModal';
import { OrderDetailModal } from './Restaurant/OrderDetailModal';
import { MenuFormModal } from './Restaurant/MenuFormModal';

const CONFIRMABLE_STATUSES = new Set<string>(['PENDING', 'PREPARED']);
const EMPTY_ORDERS: RestaurantOrderResponse[] = [];
const EMPTY_MENU_ITEMS: MenuItemResponse[] = [];

type OrderSortField = 'orderDate' | 'roomNumber' | 'guestDisplayName';
type SortDir = 'asc' | 'desc';

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
    <M3TableRow>
      <M3TableCell className="font-medium">{mi.name}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{mi.category}</M3TableCell>
      <M3TableCell className="text-right">{formatCurrencyFn(mi.price)}</M3TableCell>
      <M3TableCell className="text-center">
        <M3StatusChip
          label={mi.available ? tLabel('menu_available_yes') : tLabel('menu_available_no')}
          tone={mi.available ? 'success' : 'neutral'}
        />
      </M3TableCell>
      <M3TableCell className="text-right">
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
      </M3TableCell>
    </M3TableRow>
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
      <M3TableCell className="font-medium">{order.roomNumber ?? '—'}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{order.guestDisplayName ?? '—'}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{formatDate(order.orderDate)}</M3TableCell>
      <M3TableCell className="font-medium">{formatCurrency(order.totalAmount)}</M3TableCell>
      <M3TableCell>
        <M3StatusChip label={t(`order_status_${order.status}`, order.status.replace(/_/g, ' '))} tone={getStatusTone(order.status)} />
      </M3TableCell>
      <M3TableCell className="text-right">
        <div className="flex justify-end gap-2">
          {isConfirmable && (
            <M3TableActionLink
              onClick={handleConfirmClick}
              disabled={isConfirming}
              aria-label={`${t('confirm_order')} ${order.id}`}
              className="flex items-center gap-1"
            >
              {isConfirming
                ? <MaterialIcon name="progress_activity" size={14} className="animate-spin" aria-hidden="true" />
                : t('confirm')}
            </M3TableActionLink>
          )}
          <M3TableActionLink onClick={handleViewClick} aria-label={`${t('view')} ${order.id}`}>
            {t('view')}
          </M3TableActionLink>
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

  const queryClient = useQueryClient();
  const [isOrderModalOpen, setIsOrderModalOpen] = useState(false);
  const [selectedOrder, setSelectedOrder] = useState<RestaurantOrderResponse | null>(null);

  const [menuFormTarget, setMenuFormTarget] = useState<MenuItemResponse | 'new' | null>(null);
  const [deletingMenuId, setDeletingMenuId] = useState<string | null>(null);

  const [sortField, setSortField] = useState<OrderSortField>('orderDate');
  const [sortDir, setSortDir] = useState<SortDir>('desc');

  const { data: ordersData, isLoading: loading, error: queryError, refetch } = useOrders();
  const orders = ordersData ?? EMPTY_ORDERS;
  const error = queryError ? getErrorMessage(queryError, t('failed_load_orders')) : null;
  const handleRetry = useCallback(() => { refetch(); }, [refetch]);

  const { data: menuItemsData } = useMenuItems(isAdminOrOwner);
  const menuItems = menuItemsData ?? EMPTY_MENU_ITEMS;

  const confirmOrderMutation = useConfirmOrder();
  const confirmingId = confirmOrderMutation.isPending
    ? (confirmOrderMutation.variables ?? null)
    : null;

  const handleConfirm = useCallback(async (orderId: string) => {
    try {
      await confirmOrderMutation.mutateAsync(orderId);
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('confirm_order_failed')), 'error');
    }
  }, [confirmOrderMutation, t, addToast]);

  const handleMenuSaved = useCallback(() => {
    setMenuFormTarget(null);
    queryClient.invalidateQueries({ queryKey: queryKeys.menuItems.all });
  }, [queryClient]);

  const handleMenuEdit = useCallback((mi: MenuItemResponse) => setMenuFormTarget(mi), []);
  const handleOpenMenuForm = useCallback(() => setMenuFormTarget('new'), []);
  const handleCloseMenuForm = useCallback(() => setMenuFormTarget(null), []);

  const deleteMenuItemMutation = useDeleteMenuItem();
  const handleDeleteMenuItem = useCallback(async (item: MenuItemResponse) => {
    const confirmed = window.confirm(tMenu('menu_delete_confirm', { name: item.name }));
    if (!confirmed) return;
    setDeletingMenuId(item.id);
    try {
      await deleteMenuItemMutation.mutateAsync(item.id);
      addToast(tMenu('menu_delete_success'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, tMenu('menu_delete_error')), 'error');
    } finally {
      setDeletingMenuId(null);
    }
  }, [addToast, deleteMenuItemMutation, tMenu]);

  const handleOrderCreated = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: queryKeys.fbOrders.all });
  }, [queryClient]);

  const handleSortFieldChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    setSortField(e.target.value as OrderSortField);
  }, []);

  const toggleSortDir = useCallback(() => {
    setSortDir((prev) => (prev === 'asc' ? 'desc' : 'asc'));
  }, []);

  const sortedOrders = useMemo(() => {
    const sorted = [...orders].sort((a, b) => {
      const cmp = (a[sortField] ?? '').localeCompare(b[sortField] ?? '');
      return sortDir === 'asc' ? cmp : -cmp;
    });
    return sorted;
  }, [orders, sortField, sortDir]);

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
    t('room_label'),
    t('guest_name'),
    t('date'),
    t('total_amount'),
    t('status'),
    <span key="sr" className="sr-only">{t('actions')}</span>
  ], [t]);

  const sortOptions = useMemo(() => [
    { value: 'orderDate', label: t('date') },
    { value: 'roomNumber', label: t('room_label') },
    { value: 'guestDisplayName', label: t('guest_name') },
  ], [t]);

  const menuHeaders = useMemo(() => [
    tMenu('menu_name'),
    tMenu('menu_category'),
    tMenu('menu_price'),
    tMenu('menu_available'),
    t('actions'),
  ], [t, tMenu]);

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
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <M3Select
              label={t('sort_by')}
              hideLabel
              options={sortOptions}
              value={sortField}
              onChange={handleSortFieldChange}
            />
            <button
              type="button"
              onClick={toggleSortDir}
              aria-label={sortDir === 'asc' ? t('sort_dir_asc') : t('sort_dir_desc')}
              className="flex items-center justify-center w-10 h-10 rounded-shape-full border border-outline text-on-surface-variant hover:bg-primary/[0.08] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 transition-colors"
            >
              <MaterialIcon name={sortDir === 'asc' ? 'arrow_upward' : 'arrow_downward'} size={20} />
            </button>
          </div>
          <M3Button icon="add" onClick={handleOpenOrderModal}>{t('new_order')}</M3Button>
        </div>
      </div>

      {loading ? (
        <M3LoadingState label={t('loading')} />
      ) : error ? (
        <M3ErrorState
          title={t('error_loading_orders')}
          message={error}
          retryLabel={t('try_again')}
          onRetry={handleRetry}
        />
      ) : (
        <M3Table headers={tableHeaders}>
          {sortedOrders.length === 0 ? (
            <M3TableEmptyRow colSpan={tableHeaders.length} message={t('no_orders')} />
          ) : (
            sortedOrders.map((order) => (
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
            <M3Table headers={menuHeaders}>
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
            </M3Table>
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
