export type GuestSortDir = 'asc' | 'desc';

export const DEFAULT_GUEST_SORT_DIR: GuestSortDir = 'asc';

export interface GuestListParams {
  /** Zero-based page index; the URL carries it one-based (`?page=3`). */
  page: number;
  dir: GuestSortDir;
}

/**
 * Reads the list state from the query string (`?dir=&page=`). A missing or malformed value is
 * ignored in silence and falls back to the default: a hand-edited URL is an approximation of
 * intent, not a mistake. The search text is never part of it (it holds guest names).
 */
export const readGuestListParams = (params: URLSearchParams): GuestListParams => {
  const rawPage = Number(params.get('page'));
  const dir = params.get('dir');
  return {
    page: Number.isInteger(rawPage) && rawPage >= 2 ? rawPage - 1 : 0,
    dir: dir === 'asc' || dir === 'desc' ? dir : DEFAULT_GUEST_SORT_DIR,
  };
};

/** Query string for a list state; values equal to the default are left out to keep the URL clean. */
export const guestListParamsToSearch = ({ page, dir }: GuestListParams): URLSearchParams => {
  const search = new URLSearchParams();
  if (dir !== DEFAULT_GUEST_SORT_DIR) search.set('dir', dir);
  if (page > 0) search.set('page', String(page + 1));
  return search;
};
