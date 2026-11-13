import type { InvoiceStatus } from '../../types';

export type BillingStatusFilter = InvoiceStatus | 'ALL';
export type BillingSortField = 'issueDate' | 'totalAmount' | 'status';
export type BillingSortDir = 'asc' | 'desc';

export const BILLING_STATUS_FILTERS = ['ALL', 'ISSUED', 'PAID', 'CANCELLED'] as const;
const SORT_FIELDS: readonly BillingSortField[] = ['issueDate', 'totalAmount', 'status'];
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export const DEFAULT_BILLING_SORT_FIELD: BillingSortField = 'issueDate';
export const DEFAULT_BILLING_SORT_DIR: BillingSortDir = 'desc';

export interface BillingListParams {
  status: BillingStatusFilter;
  sortField: BillingSortField;
  sortDir: BillingSortDir;
  /** Zero-based page index; the URL carries it one-based (`?page=3`). */
  page: number;
  /** ISO dates (`yyyy-mm-dd`) or an empty string when the bound is not set. */
  dateFrom: string;
  dateTo: string;
}

/** Older entry points (the dashboard card) passed the status as navigation state. */
export interface BillingNavState {
  statusFilter?: BillingStatusFilter;
}

const readDate = (value: string | null): string => (value !== null && ISO_DATE.test(value) ? value : '');

/**
 * Reads the list state from the query string (`?status=&sort=&dir=&page=&from=&to=`). A missing or
 * malformed value is ignored in silence and falls back to the navigation state, then to the
 * default: a hand-edited URL is an approximation of intent, not a mistake. The search text is
 * never part of it (it holds guest names and emails).
 */
export const readBillingListParams = (
  params: URLSearchParams,
  nav: BillingNavState | null,
): BillingListParams => {
  const status = BILLING_STATUS_FILTERS.find((s) => s === params.get('status'));
  const sortField = SORT_FIELDS.find((f) => f === params.get('sort'));
  const dir = params.get('dir');
  const rawPage = Number(params.get('page'));
  return {
    status: status ?? nav?.statusFilter ?? 'ALL',
    sortField: sortField ?? DEFAULT_BILLING_SORT_FIELD,
    sortDir: dir === 'asc' || dir === 'desc' ? dir : DEFAULT_BILLING_SORT_DIR,
    page: Number.isInteger(rawPage) && rawPage >= 2 ? rawPage - 1 : 0,
    dateFrom: readDate(params.get('from')),
    dateTo: readDate(params.get('to')),
  };
};

/**
 * Query string for a list state; sort, page and date values equal to the default are left out to
 * keep the URL clean. The status is always written: an explicit `status=ALL` is what stops an
 * older navigation state (`statusFilter`) from bringing the previous filter back.
 */
export const billingListParamsToSearch = (state: BillingListParams): URLSearchParams => {
  const search = new URLSearchParams({ status: state.status });
  if (state.sortField !== DEFAULT_BILLING_SORT_FIELD) search.set('sort', state.sortField);
  if (state.sortDir !== DEFAULT_BILLING_SORT_DIR) search.set('dir', state.sortDir);
  if (state.page > 0) search.set('page', String(state.page + 1));
  if (state.dateFrom) search.set('from', state.dateFrom);
  if (state.dateTo) search.set('to', state.dateTo);
  return search;
};
