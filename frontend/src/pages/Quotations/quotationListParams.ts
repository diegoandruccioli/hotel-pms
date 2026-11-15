export interface QuotationListParams {
  /** Zero-based page index; the URL carries it one-based (`?page=3`). */
  page: number;
}

/**
 * Reads the list state from the query string (`?page=`). A missing or malformed value is ignored in
 * silence and falls back to the first page: a hand-edited URL is an approximation of intent, not a
 * mistake.
 */
export const readQuotationListParams = (params: URLSearchParams): QuotationListParams => {
  const rawPage = Number(params.get('page'));
  return { page: Number.isInteger(rawPage) && rawPage >= 2 ? rawPage - 1 : 0 };
};

/** Query string for a list state; the first page is left out to keep the URL clean. */
export const quotationListParamsToSearch = ({ page }: QuotationListParams): URLSearchParams => {
  const search = new URLSearchParams();
  if (page > 0) search.set('page', String(page + 1));
  return search;
};
