import { useFormatters } from '../hooks';
import { useState, useCallback, memo, useMemo } from 'react';
import type { ColumnDef, SortingState } from '@tanstack/react-table';
import type { RestaurantOrderResponse } from '../types';
import { MaterialIcon } from '../components/MaterialIcon';
import { PageHeader } from '../components/PageHeader';
import { M3Button } from '../components/m3';
import { M3DataTable } from '../components/m3';
import { M3FilterChip } from '../components/m3';
import { M3StatusChip } from '../components/m3';
import { M3TableActionLink } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useAuthStore } from '../store';
import { useToastStore } from '../store';
import { getErrorMessage, orderStatusTone, matchesOrderFilter, ORDER_FILTERS } from '../utils';
import type { OrderFilter } from '../utils';
import { useQueryClient } from '@tanstack/react-query';
import { useOrders, useConfirmOrder } from '../hooks/queries';
import { queryKeys } from '../lib';
import { OrderFormModal } from './Restaurant/OrderFormModal';
import { OrderDetailModal } from './Restaurant/OrderDetailModal';
import { MenuSection } from './Restaurant/MenuSection';

const CONFIRMABLE_STATUSES = new Set<string>(['PENDING', 'PREPARED']);
const EMPTY_ORDERS: RestaurantOrderResponse[] = [];

const FILTER_LABEL_KEYS: Record<OrderFilter, string> = {
  ALL: 'order_filter_all',
  OPEN: 'order_filter_open',
  CLOSED: 'order_filter_closed',
  CANCELLED: 'order_filter_cancelled',
};

type OrderSortField = 'orderDate' | 'roomNumber' | 'guestDisplayName';
type SortDir = 'asc' | 'desc';
const DEFAULT_ORDER_SORT_FIELD: OrderSortField = 'orderDate';
const DEFAULT_ORDER_SORT_DIR: SortDir = 'desc';

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
  const { t } = useTranslation('common');
  const role = useAuthStore((s) => s.user?.role);
  const { addToast } = useToastStore();
  const isAdminOrOwner = role === 'ADMIN' || role === 'OWNER';

  const queryClient = useQueryClient();
  const [isOrderModalOpen, setIsOrderModalOpen] = useState(false);
  const [selectedOrder, setSelectedOrder] = useState<RestaurantOrderResponse | null>(null);
  const [orderFilter, setOrderFilter] = useState<OrderFilter>('ALL');

  const [sortField, setSortField] = useState<OrderSortField>(DEFAULT_ORDER_SORT_FIELD);
  const [sortDir, setSortDir] = useState<SortDir>(DEFAULT_ORDER_SORT_DIR);

  const { data: ordersData, isLoading: loading, error: queryError, refetch } = useOrders();
  const orders = ordersData ?? EMPTY_ORDERS;
  const error = queryError ? getErrorMessage(queryError, t('failed_load_orders')) : null;
  const handleRetry = useCallback(() => { refetch(); }, [refetch]);

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

  const visibleOrders = useMemo(() => {
    const filtered = orders.filter((o) => matchesOrderFilter(o.status, orderFilter));
    return filtered.sort((a, b) => {
      const cmp = (a[sortField] ?? '').localeCompare(b[sortField] ?? '');
      return sortDir === 'asc' ? cmp : -cmp;
    });
  }, [orders, orderFilter, sortField, sortDir]);

  const openOrdersCount = useMemo(
    () => orders.filter((o) => matchesOrderFilter(o.status, 'OPEN')).length,
    [orders],
  );

  const filterLabel = useCallback((f: OrderFilter) => t(FILTER_LABEL_KEYS[f]), [t]);

  const handleOpenOrderModal = useCallback(() => setIsOrderModalOpen(true), []);
  const handleCloseOrderModal = useCallback(() => setIsOrderModalOpen(false), []);
  const handleViewOrder = useCallback((order: RestaurantOrderResponse) => setSelectedOrder(order), []);
  const handleCloseDetail = useCallback(() => setSelectedOrder(null), []);

  const { formatCurrency, formatDate } = useFormatters();

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
          tone={orderStatusTone[row.original.status]}
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

  return (
    <div className="space-y-6">
      <PageHeader
        icon="restaurant"
        title={t('nav_restaurant')}
        subtitle={ordersData ? t('restaurant_open_orders', { open: openOrdersCount }) : t('restaurant_subtitle')}
        actions={
          <M3Button icon="add" onClick={handleOpenOrderModal}>{t('new_order')}</M3Button>
        }
      />

      <div role="group" aria-label={t('order_filter_label')} className="flex flex-wrap items-center gap-2">
        {ORDER_FILTERS.map((f) => (
          <M3FilterChip
            key={f}
            label={filterLabel(f)}
            value={f}
            selected={orderFilter === f}
            onValueSelect={setOrderFilter}
          />
        ))}
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
          data={visibleOrders}
          columns={orderColumns}
          getRowId={getOrderRowId}
          sorting={orderSorting}
          onSortingChange={handleOrderSortingChange}
          emptyMessage={t('no_orders')}
        />
      )}

      {isAdminOrOwner && <MenuSection />}

      {isOrderModalOpen && (
        <OrderFormModal onClose={handleCloseOrderModal} onCreated={handleOrderCreated} />
      )}

      {selectedOrder && (
        <OrderDetailModal order={selectedOrder} onClose={handleCloseDetail} />
      )}
    </div>
  );
});
