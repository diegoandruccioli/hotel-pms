import { ORDER_FILTERS, type OrderFilter } from '../../utils';

export type OrderSortField = 'orderDate' | 'roomNumber' | 'guestDisplayName';
export type OrderSortDir = 'asc' | 'desc';

const SORT_FIELDS: readonly OrderSortField[] = ['orderDate', 'roomNumber', 'guestDisplayName'];

export const DEFAULT_ORDER_SORT_FIELD: OrderSortField = 'orderDate';
export const DEFAULT_ORDER_SORT_DIR: OrderSortDir = 'desc';

export interface OrderListParams {
  status: OrderFilter;
  sortField: OrderSortField;
  sortDir: OrderSortDir;
}

/**
 * Reads the list state from the query string (`?status=&sort=&dir=`). A missing or malformed value
 * is ignored in silence and falls back to the default: a hand-edited URL is an approximation of
 * intent, not a mistake.
 */
export const readOrderListParams = (params: URLSearchParams): OrderListParams => {
  const status = ORDER_FILTERS.find((f) => f === params.get('status'));
  const sortField = SORT_FIELDS.find((f) => f === params.get('sort'));
  const dir = params.get('dir');
  return {
    status: status ?? 'ALL',
    sortField: sortField ?? DEFAULT_ORDER_SORT_FIELD,
    sortDir: dir === 'asc' || dir === 'desc' ? dir : DEFAULT_ORDER_SORT_DIR,
  };
};

/** Query string for a list state; values equal to the default are left out to keep the URL clean. */
export const orderListParamsToSearch = (state: OrderListParams): URLSearchParams => {
  const search = new URLSearchParams();
  if (state.status !== 'ALL') search.set('status', state.status);
  if (state.sortField !== DEFAULT_ORDER_SORT_FIELD) search.set('sort', state.sortField);
  if (state.sortDir !== DEFAULT_ORDER_SORT_DIR) search.set('dir', state.sortDir);
  return search;
};
