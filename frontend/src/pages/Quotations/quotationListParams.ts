import type { QuotationStatus } from '../../types';

export type QuotationStatusFilter = QuotationStatus | 'ALL';

export const QUOTATION_STATUS_FILTERS = ['ALL', 'DRAFT', 'SENT', 'ACCEPTED', 'DECLINED', 'EXPIRED'] as const;

export interface QuotationListParams {
  status: QuotationStatusFilter;
  /** Zero-based page index; the URL carries it one-based (`?page=3`). */
  page: number;
}

/**
 * Reads the list state from the query string (`?status=&page=`). A missing or malformed value is
 * ignored in silence and falls back to the default: a hand-edited URL is an approximation of
 * intent, not a mistake. The search text is never part of it (it holds guest names and emails).
 */
export const readQuotationListParams = (params: URLSearchParams): QuotationListParams => {
  const rawPage = Number(params.get('page'));
  return {
    status: QUOTATION_STATUS_FILTERS.find((s) => s === params.get('status')) ?? 'ALL',
    page: Number.isInteger(rawPage) && rawPage >= 2 ? rawPage - 1 : 0,
  };
};

/** Query string for a list state; the default ("all", first page) is left out to keep the URL clean. */
export const quotationListParamsToSearch = ({ status, page }: QuotationListParams): URLSearchParams => {
  const search = new URLSearchParams();
  if (status !== 'ALL') search.set('status', status);
  if (page > 0) search.set('page', String(page + 1));
  return search;
};
