export type UserFilter = 'ALL' | 'ADMIN' | 'OWNER' | 'RECEPTIONIST' | 'DEACTIVATED';
export type UserSortField = 'username' | 'email' | 'role' | 'active';
export type UserSortDir = 'asc' | 'desc';

export const USER_FILTERS: readonly UserFilter[] = ['ALL', 'ADMIN', 'OWNER', 'RECEPTIONIST', 'DEACTIVATED'];
const SORT_FIELDS: readonly UserSortField[] = ['username', 'email', 'role', 'active'];

export const DEFAULT_USER_SORT_FIELD: UserSortField = 'username';
export const DEFAULT_USER_SORT_DIR: UserSortDir = 'asc';

export interface UserListParams {
  filter: UserFilter;
  sortField: UserSortField;
  sortDir: UserSortDir;
}

/**
 * Reads the list state from the query string (`?filter=&sort=&dir=`). A missing or malformed value
 * is ignored in silence and falls back to the default: a hand-edited URL is an approximation of
 * intent, not a mistake. The search text is never part of it (it holds usernames and emails).
 */
export const readUserListParams = (params: URLSearchParams): UserListParams => {
  const filter = USER_FILTERS.find((f) => f === params.get('filter'));
  const sortField = SORT_FIELDS.find((f) => f === params.get('sort'));
  const dir = params.get('dir');
  return {
    filter: filter ?? 'ALL',
    sortField: sortField ?? DEFAULT_USER_SORT_FIELD,
    sortDir: dir === 'asc' || dir === 'desc' ? dir : DEFAULT_USER_SORT_DIR,
  };
};

/** Query string for a list state; values equal to the default are left out to keep the URL clean. */
export const userListParamsToSearch = (state: UserListParams): URLSearchParams => {
  const search = new URLSearchParams();
  if (state.filter !== 'ALL') search.set('filter', state.filter);
  if (state.sortField !== DEFAULT_USER_SORT_FIELD) search.set('sort', state.sortField);
  if (state.sortDir !== DEFAULT_USER_SORT_DIR) search.set('dir', state.sortDir);
  return search;
};
