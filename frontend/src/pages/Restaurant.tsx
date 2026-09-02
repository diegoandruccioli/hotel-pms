import { useState, useCallback, memo, useMemo } from 'react';
import type { ColumnDef, SortingState } from '@tanstack/react-table';
import type { MenuItemResponse, RestaurantOrderResponse, OrderStatus } from '../types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3DataTable } from '../components/m3/M3DataTable';
import { M3StatusChip } from '../components/m3/M3StatusChip';
import { M3Card } from '../components/m3/M3Card';
import { M3TableActionLink } from '../components/m3/M3TableActionLink';
import { M3LoadingState } from '../components/m3/M3LoadingState';
import { M3ErrorState } from '../components/m3/M3ErrorState';
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
const DEFAULT_ORDER_SORT_FIELD: OrderSortField = 'orderDate';
const DEFAULT_ORDER_SORT_DIR: SortDir = 'desc';
const DEFAULT_MENU_SORT_FIELD = 'name';
const DEFAULT_MENU_SORT_DIR: SortDir = 'asc';

interface MenuActionsCellProps {
  mi: MenuItemResponse;
  deletingMenuId: string | null;
  onEdit: (mi: MenuItemResponse) => void;
  onDelete: (mi: MenuItemResponse) => void;
  tLabel: (key: string) => string;
  tCommon: (key: string) => string;
}

const MenuActionsCell = ({ mi, deletingMenuId, onEdit, onDelete, tLabel, tCommon }: MenuActionsCellProps) => {
  const handleEdit = useCallback(() => onEdit(mi), [onEdit, mi]);
  const handleDelete = useCallback(() => onDelete(mi), [onDelete, mi]);
  return (
    <div className="flex justify-end gap-2">
      <button type="button" onClick={handleEdit}
        className="text-primary hover:text-primary/80 text-xs font-medium focus:outline-hidden focus:ring-2 focus:ring-primary rounded-sm"
        aria-label={`${tLabel('menu_edit_item')} ${mi.name}`}>
        {tCommon('edit')}
      </button>
      <button type="button" onClick={handleDelete}
        disabled={deletingMenuId === mi.id}
        className="text-error hover:text-error/80 text-xs font-medium disabled:opacity-50 focus:outline-hidden focus:ring-2 focus:ring-error rounded-sm"
        aria-label={`${tLabel('menu_delete_item')} ${mi.name}`}>
        {tCommon('delete')}
      </button>
    </div>
  );
};

function compareMenuItems(a: MenuItemResponse, b: MenuItemResponse, field: string): number {
  switch (field) {
    case 'category': return a.category.localeCompare(b.category);
    case 'price': return a.price - b.price;
    case 'available': return Number(a.available) - Number(b.available);
    default: return a.name.localeCompare(b.name);
  }
}

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

interface OrderActionsCellProps {
  order: RestaurantOrderResponse;
  confirmingId: string | null;
  onConfirm: (id: string) => void;
  onView: (order: RestaurantOrderResponse) => void;
  t: TFunction<'common'>;
}

const OrderActionsCell = ({ order, confirmingId, onConfirm, onView, t }: OrderActionsCellProps) => {
  const isConfirmable = CONFIRMABLE_STATUSES.has(order.status);
  const isConfirming = confirmingId === order.id;
  const handleConfirmClick = useCallback(() => onConfirm(order.id), [onConfirm, order.id]);
  const handleViewClick = useCallback(() => onView(order), [onView, order]);

  return (
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
  );
};

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

  const [sortField, setSortField] = useState<OrderSortField>(DEFAULT_ORDER_SORT_FIELD);
  const [sortDir, setSortDir] = useState<SortDir>(DEFAULT_ORDER_SORT_DIR);
  const [menuSortField, setMenuSortField] = useState(DEFAULT_MENU_SORT_FIELD);
  const [menuSortDir, setMenuSortDir] = useState<SortDir>(DEFAULT_MENU_SORT_DIR);

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

  const orderSorting = useMemo<SortingState>(
    () => [{ id: sortField, desc: sortDir === 'desc' }],
    [sortField, sortDir],
  );

  const handleOrderSortingChange = useCallback((next: SortingState) => {
    setSortField(next[0].id as OrderSortField);
    setSortDir(next[0].desc ? 'desc' : 'asc');
  }, []);

  const sortedOrders = useMemo(() => {
    const sorted = [...orders].sort((a, b) => {
      const cmp = (a[sortField] ?? '').localeCompare(b[sortField] ?? '');
      return sortDir === 'asc' ? cmp : -cmp;
    });
    return sorted;
  }, [orders, sortField, sortDir]);

  const menuSorting = useMemo<SortingState>(
    () => [{ id: menuSortField, desc: menuSortDir === 'desc' }],
    [menuSortField, menuSortDir],
  );

  const handleMenuSortingChange = useCallback((next: SortingState) => {
    setMenuSortField(next[0].id);
    setMenuSortDir(next[0].desc ? 'desc' : 'asc');
  }, []);

  const sortedMenuItems = useMemo(() => {
    const sign = menuSortDir === 'desc' ? -1 : 1;
    return [...menuItems].sort((a, b) => sign * compareMenuItems(a, b, menuSortField));
  }, [menuItems, menuSortField, menuSortDir]);

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

  const getOrderRowId = useCallback((o: RestaurantOrderResponse) => o.id, []);

  const orderColumns = useMemo<ColumnDef<RestaurantOrderResponse>[]>(() => [
    {
      id: 'roomNumber',
      accessorKey: 'roomNumber',
      header: t('room_label'),
      cell: ({ row }) => <span className="font-medium">{row.original.roomNumber ?? '—'}</span>,
    },
    {
      id: 'guestDisplayName',
      accessorKey: 'guestDisplayName',
      header: t('guest_name'),
      cell: ({ row }) => <span className="text-on-surface-variant">{row.original.guestDisplayName ?? '—'}</span>,
    },
    {
      id: 'orderDate',
      accessorKey: 'orderDate',
      header: t('date'),
      cell: ({ row }) => <span className="text-on-surface-variant">{formatDate(row.original.orderDate)}</span>,
    },
    {
      id: 'totalAmount',
      header: t('total_amount'),
      enableSorting: false,
      cell: ({ row }) => <span className="font-medium">{formatCurrency(row.original.totalAmount)}</span>,
    },
    {
      id: 'status',
      header: t('status'),
      enableSorting: false,
      cell: ({ row }) => (
        <M3StatusChip
          label={t(`order_status_${row.original.status}`, row.original.status.replace(/_/g, ' '))}
          tone={getStatusTone(row.original.status)}
        />
      ),
    },
    {
      id: 'actions',
      header: () => <span className="sr-only">{t('actions')}</span>,
      enableSorting: false,
      cell: ({ row }) => (
        <OrderActionsCell order={row.original} confirmingId={confirmingId} onConfirm={handleConfirm} onView={handleViewOrder} t={t} />
      ),
    },
  ], [t, formatDate, formatCurrency, confirmingId, handleConfirm, handleViewOrder]);

  const getMenuItemRowId = useCallback((mi: MenuItemResponse) => mi.id, []);

  const menuColumns = useMemo<ColumnDef<MenuItemResponse>[]>(() => [
    {
      id: 'name',
      accessorKey: 'name',
      header: tMenu('menu_name'),
      cell: ({ row }) => <span className="font-medium">{row.original.name}</span>,
    },
    {
      id: 'category',
      accessorKey: 'category',
      header: tMenu('menu_category'),
      cell: ({ row }) => <span className="text-on-surface-variant">{row.original.category}</span>,
    },
    {
      id: 'price',
      accessorKey: 'price',
      header: tMenu('menu_price'),
      cell: ({ row }) => <span className="text-right block">{formatCurrency(row.original.price)}</span>,
    },
    {
      id: 'available',
      accessorKey: 'available',
      header: tMenu('menu_available'),
      cell: ({ row }) => (
        <div className="text-center">
          <M3StatusChip
            label={row.original.available ? tMenu('menu_available_yes') : tMenu('menu_available_no')}
            tone={row.original.available ? 'success' : 'neutral'}
          />
        </div>
      ),
    },
    {
      id: 'actions',
      header: t('actions'),
      enableSorting: false,
      cell: ({ row }) => (
        <MenuActionsCell
          mi={row.original}
          deletingMenuId={deletingMenuId}
          onEdit={handleMenuEdit}
          onDelete={handleDeleteMenuItem}
          tLabel={tMenu}
          tCommon={t}
        />
      ),
    },
  ], [t, tMenu, formatCurrency, deletingMenuId, handleMenuEdit, handleDeleteMenuItem]);

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
        <M3DataTable
          data={sortedOrders}
          columns={orderColumns}
          getRowId={getOrderRowId}
          sorting={orderSorting}
          onSortingChange={handleOrderSortingChange}
          emptyMessage={t('no_orders')}
        />
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
            <M3DataTable
              data={sortedMenuItems}
              columns={menuColumns}
              getRowId={getMenuItemRowId}
              sorting={menuSorting}
              onSortingChange={handleMenuSortingChange}
              emptyMessage={tMenu('menu_no_items')}
            />
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
