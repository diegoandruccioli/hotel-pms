import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { ColumnDef, SortingState } from '@tanstack/react-table';
import { userService } from '../services';
import type { UserResponse } from '../types';
import { MaterialIcon } from '../components/MaterialIcon';
import { ListToolbar } from '../components/ListToolbar';
import { M3Avatar, M3Button, M3Card } from '../components/m3';
import { M3ConfirmDialog } from '../components/m3';
import { M3DataTable } from '../components/m3';
import { M3FilterChip } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3EmptyState } from '../components/m3';
import { M3StatusChip } from '../components/m3';
import { SettingsPageHeader } from '../components/SettingsPageHeader';
import { useToastStore } from '../store';
import { useAuthStore } from '../store';
import { getErrorMessage, userRoleTone } from '../utils';
import { CreateUserModal } from './AdminUsers/CreateUserModal';
import { ResetPasswordModal } from './AdminUsers/ResetPasswordModal';
import type { TFunction } from 'i18next';

type UserFilter = 'ALL' | 'ADMIN' | 'OWNER' | 'RECEPTIONIST' | 'DEACTIVATED';

const USER_FILTERS: readonly UserFilter[] = ['ALL', 'ADMIN', 'OWNER', 'RECEPTIONIST', 'DEACTIVATED'];

const FILTER_LABEL_KEYS: Record<UserFilter, string> = {
  ALL: 'filter_all',
  ADMIN: 'users_filter_admin',
  OWNER: 'users_filter_owner',
  RECEPTIONIST: 'users_filter_receptionist',
  DEACTIVATED: 'users_filter_deactivated',
};

const matchesFilter = (user: UserResponse, filter: UserFilter): boolean => {
  if (filter === 'ALL') return true;
  if (filter === 'DEACTIVATED') return !user.active;
  return user.role === filter;
};

const ROLE_DESCRIPTIONS = [
  ['ADMIN', 'users_role_admin_desc'],
  ['OWNER', 'users_role_owner_desc'],
  ['RECEPTIONIST', 'users_role_receptionist_desc'],
] as const;

const DEFAULT_SORT_FIELD = 'username';
const DEFAULT_SORT_DIR: 'asc' | 'desc' = 'asc';

interface ActionsCellProps {
  user: UserResponse;
  onToggle: (u: UserResponse) => void;
  onResetPassword: (u: UserResponse) => void;
  currentUsername: string | undefined;
  t: TFunction;
}

const ACTION_BTN_CLASS = 'h-auto min-h-10 px-3 py-1 text-xs';

const ActionsCell = ({ user, onToggle, onResetPassword, currentUsername, t }: ActionsCellProps) => {
  const handleToggle = useCallback(() => onToggle(user), [onToggle, user]);
  const handleReset = useCallback(() => onResetPassword(user), [onResetPassword, user]);

  return (
    <div className="flex items-center gap-2">
      <M3Button type="button" variant="outlined" onClick={handleToggle}
        className={ACTION_BTN_CLASS}
        aria-label={user.active ? t('btn_deactivate') : t('btn_activate')}>
        {user.active ? t('btn_deactivate') : t('btn_activate')}
      </M3Button>
      {user.username !== currentUsername && (
        <M3Button type="button" variant="outlined" onClick={handleReset}
          className={ACTION_BTN_CLASS}
          aria-label={`${t('btn_reset_password')} ${user.username}`}>
          {t('btn_reset_password')}
        </M3Button>
      )}
    </div>
  );
};

function compareUsers(a: UserResponse, b: UserResponse, field: string): number {
  switch (field) {
    case 'email': return a.email.localeCompare(b.email);
    case 'role': return a.role.localeCompare(b.role);
    case 'active': return Number(a.active) - Number(b.active);
    default: return a.username.localeCompare(b.username);
  }
}

export function AdminUsers() {
  const { t } = useTranslation('admin');
  const navigate = useNavigate();
  const { addToast } = useToastStore();
  const currentUser = useAuthStore((s) => s.user);
  const handleBack = useCallback(() => navigate(-1), [navigate]);
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [resetTarget, setResetTarget] = useState<UserResponse | null>(null);
  const [deactivateTarget, setDeactivateTarget] = useState<UserResponse | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [filter, setFilter] = useState<UserFilter>('ALL');
  const [sortField, setSortField] = useState(DEFAULT_SORT_FIELD);
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>(DEFAULT_SORT_DIR);

  const openCreate = useCallback(() => setShowCreate(true), []);
  const closeCreate = useCallback(() => setShowCreate(false), []);
  const openReset = useCallback((u: UserResponse) => setResetTarget(u), []);
  const closeReset = useCallback(() => setResetTarget(null), []);

  const load = useCallback(() => {
    userService
      .listUsers()
      .then(setUsers)
      .catch(() => addToast(t('err_load_failed'), 'error'))
      .finally(() => setLoading(false));
  }, [addToast, t]);

  useEffect(() => {
    load();
  }, [load]);

  const handleCreated = useCallback(
    (u: UserResponse) => {
      setUsers((prev) => [u, ...prev]);
      closeCreate();
      addToast(t('toast_created', { username: u.username }), 'success');
    },
    [addToast, t, closeCreate],
  );

  const handleResetSuccess = useCallback(() => {
    closeReset();
    addToast(t('toast_reset_success'), 'success');
  }, [closeReset, addToast, t]);

  const performToggle = useCallback(
    async (u: UserResponse) => {
      try {
        const updated = u.active
          ? await userService.deactivateUser(u.id)
          : await userService.activateUser(u.id);
        setUsers((prev) => prev.map((x) => (x.id === updated.id ? updated : x)));
        addToast(
          u.active
            ? t('toast_deactivated', { username: u.username })
            : t('toast_activated', { username: u.username }),
          'success',
        );
      } catch (err: unknown) {
        addToast(getErrorMessage(err, t('err_toggle_failed')), 'error');
      }
    },
    [addToast, t],
  );

  // Locking someone out asks first; re-activating is harmless and goes straight through.
  const handleToggle = useCallback(
    (u: UserResponse) => {
      if (u.active) setDeactivateTarget(u);
      else void performToggle(u);
    },
    [performToggle],
  );

  const cancelDeactivate = useCallback(() => setDeactivateTarget(null), []);
  const confirmDeactivate = useCallback(() => {
    if (deactivateTarget) void performToggle(deactivateTarget);
    setDeactivateTarget(null);
  }, [deactivateTarget, performToggle]);

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
  }, []);

  const activeCount = useMemo(() => users.filter((u) => u.active).length, [users]);

  const visibleUsers = useMemo(() => {
    const needle = searchQuery.trim().toLowerCase();
    return users.filter(
      (u) => matchesFilter(u, filter)
        && (needle === '' || u.username.toLowerCase().includes(needle) || u.email.toLowerCase().includes(needle)),
    );
  }, [users, filter, searchQuery]);

  // Small, unpaginated admin-only list (a hotel's staff accounts) — sorted
  // client-side, unlike the paginated pages where M3DataTable's sorting
  // state drives a server request instead.
  const sortedUsers = useMemo(() => {
    const sign = sortDir === 'desc' ? -1 : 1;
    return [...visibleUsers].sort((a, b) => sign * compareUsers(a, b, sortField));
  }, [visibleUsers, sortField, sortDir]);

  const sorting = useMemo<SortingState>(
    () => [{ id: sortField, desc: sortDir === 'desc' }],
    [sortField, sortDir],
  );

  const handleSortingChange = useCallback((next: SortingState) => {
    setSortField(next[0].id);
    setSortDir(next[0].desc ? 'desc' : 'asc');
  }, []);

  const getUserRowId = useCallback((u: UserResponse) => u.id, []);

  const columns = useMemo<ColumnDef<UserResponse>[]>(() => [
    {
      id: 'username',
      accessorKey: 'username',
      header: t('col_username'),
      cell: ({ row }) => (
        <div className="flex items-center gap-3">
          <M3Avatar name={row.original.username} size="md" aria-hidden="true" />
          <span className="font-medium">{row.original.username}</span>
        </div>
      ),
    },
    {
      id: 'email',
      accessorKey: 'email',
      header: t('col_email'),
      cell: ({ row }) => <span className="text-on-surface-variant">{row.original.email}</span>,
    },
    {
      id: 'role',
      accessorKey: 'role',
      header: t('col_role'),
      cell: ({ row }) => (
        <M3StatusChip label={row.original.role} tone={userRoleTone[row.original.role]} />
      ),
    },
    {
      id: 'active',
      accessorKey: 'active',
      header: t('col_status'),
      cell: ({ row }) => (
        <div className="flex flex-col items-start gap-1">
          <M3StatusChip
            label={row.original.active ? t('status_active') : t('status_inactive')}
            tone={row.original.active ? 'success' : 'error'}
          />
          {row.original.mustChangePassword && (
            <span className="flex items-center gap-1 text-xs text-on-surface-variant">
              <MaterialIcon name="warning" size={14} />
              {t('must_change_pw')}
            </span>
          )}
        </div>
      ),
    },
    {
      id: 'actions',
      header: t('col_actions'),
      enableSorting: false,
      cell: ({ row }) => (
        <ActionsCell
          user={row.original}
          onToggle={handleToggle}
          onResetPassword={openReset}
          currentUsername={currentUser?.username}
          t={t}
        />
      ),
    },
  ], [t, handleToggle, openReset, currentUser?.username]);

  return (
    <div className="space-y-6">
      <SettingsPageHeader
        icon="manage_accounts"
        title={t('page_title')}
        subtitle={loading || users.length === 0 ? t('page_subtitle') : t('users_active_summary', { count: activeCount })}
        onBack={handleBack}
        actions={
          <M3Button icon="person_add" onClick={openCreate}>
            {t('btn_new_user')}
          </M3Button>
        }
      />

      {loading ? (
        <M3LoadingState label={t('loading', { ns: 'common' })} />
      ) : users.length === 0 ? (
        <M3EmptyState icon="manage_accounts" title={t('no_users')} className="bg-surface rounded-shape-md shadow-elevation-1" />
      ) : (
        <div className="grid grid-cols-1 gap-6 2xl:grid-cols-[minmax(0,1fr)_20rem]">
          <div className="space-y-4">
            <ListToolbar
              searchLabel={t('users_search_label')}
              searchPlaceholder={t('users_search_hint')}
              filtersLabel={t('users_filters_label')}
              searchValue={searchQuery}
              onSearchChange={handleSearchChange}
            >
              {USER_FILTERS.map((value) => (
                <M3FilterChip
                  key={value}
                  value={value}
                  selected={filter === value}
                  label={t(FILTER_LABEL_KEYS[value])}
                  onValueSelect={setFilter}
                />
              ))}
            </ListToolbar>
            {visibleUsers.length === 0 ? (
              <M3EmptyState icon="manage_accounts" title={t('users_no_match')} className="bg-surface rounded-shape-md shadow-elevation-1" />
            ) : (
              <M3DataTable
                data={sortedUsers}
                columns={columns}
                getRowId={getUserRowId}
                sorting={sorting}
                onSortingChange={handleSortingChange}
                emptyMessage={t('no_users')}
              />
            )}
          </div>
          <M3Card variant="solid" className="h-fit p-5">
            <h2 className="text-base font-display font-medium text-on-surface">{t('users_roles_title')}</h2>
            <dl className="mt-3 space-y-3 text-sm font-body">
              {ROLE_DESCRIPTIONS.map(([role, descKey]) => (
                <div key={role}>
                  <dt className="font-medium text-on-surface">{t(FILTER_LABEL_KEYS[role])}</dt>
                  <dd className="mt-1 text-on-surface-variant">{t(descKey)}</dd>
                </div>
              ))}
            </dl>
          </M3Card>
        </div>
      )}

      {deactivateTarget && (
        <M3ConfirmDialog
          title={t('btn_deactivate')}
          message={t('users_deactivate_confirm', { username: deactivateTarget.username })}
          onConfirm={confirmDeactivate}
          onCancel={cancelDeactivate}
        />
      )}

      {showCreate && (
        <CreateUserModal onClose={closeCreate} onCreated={handleCreated} />
      )}
      {resetTarget && (
        <ResetPasswordModal
          user={resetTarget}
          onClose={closeReset}
          onSuccess={handleResetSuccess}
        />
      )}
    </div>
  );
}
