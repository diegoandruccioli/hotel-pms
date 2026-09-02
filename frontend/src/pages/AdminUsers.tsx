import { useState, useEffect, useCallback, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import type { ColumnDef, SortingState } from '@tanstack/react-table';
import { userService } from '../services/userService';
import type { UserResponse } from '../types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3';
import { M3DataTable } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3EmptyState } from '../components/m3';
import { useToastStore } from '../store/toastStore';
import { useAuthStore } from '../store/authStore';
import { getErrorMessage } from '../utils';
import { CreateUserModal } from './AdminUsers/CreateUserModal';
import { ResetPasswordModal } from './AdminUsers/ResetPasswordModal';
import type { TFunction } from 'i18next';

const DEFAULT_SORT_FIELD = 'username';
const DEFAULT_SORT_DIR: 'asc' | 'desc' = 'asc';

interface ActionsCellProps {
  user: UserResponse;
  onToggle: (u: UserResponse) => void;
  onResetPassword: (u: UserResponse) => void;
  currentUsername: string | undefined;
  t: TFunction;
}

const ActionsCell = ({ user, onToggle, onResetPassword, currentUsername, t }: ActionsCellProps) => {
  const handleToggle = useCallback(() => onToggle(user), [onToggle, user]);
  const handleReset = useCallback(() => onResetPassword(user), [onResetPassword, user]);

  return (
    <div className="flex items-center gap-2">
      <button type="button" onClick={handleToggle}
        className="inline-flex items-center justify-center min-h-[40px] text-xs rounded-full border border-outline px-3 py-1 hover:bg-surface-variant focus:outline-hidden focus:ring-2 focus:ring-primary"
        aria-label={user.active ? t('btn_deactivate') : t('btn_activate')}>
        {user.active ? t('btn_deactivate') : t('btn_activate')}
      </button>
      {user.username !== currentUsername && (
        <button type="button" onClick={handleReset}
          className="inline-flex items-center justify-center min-h-[40px] text-xs rounded-full border border-outline px-3 py-1 hover:bg-surface-variant focus:outline-hidden focus:ring-2 focus:ring-primary"
          aria-label={`${t('btn_reset_password')} ${user.username}`}>
          {t('btn_reset_password')}
        </button>
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
  const { addToast } = useToastStore();
  const currentUser = useAuthStore((s) => s.user);
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [resetTarget, setResetTarget] = useState<UserResponse | null>(null);
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

  const handleToggle = useCallback(
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

  // Small, unpaginated admin-only list (a hotel's staff accounts) — sorted
  // client-side, unlike the paginated pages where M3DataTable's sorting
  // state drives a server request instead.
  const sortedUsers = useMemo(() => {
    const sign = sortDir === 'desc' ? -1 : 1;
    return [...users].sort((a, b) => sign * compareUsers(a, b, sortField));
  }, [users, sortField, sortDir]);

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
      cell: ({ row }) => <span className="font-medium">{row.original.username}</span>,
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
        <span className="rounded-full bg-secondary-container text-on-secondary-container px-2 py-0.5 text-xs font-medium">
          {row.original.role}
        </span>
      ),
    },
    {
      id: 'active',
      accessorKey: 'active',
      header: t('col_status'),
      cell: ({ row }) => (
        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${
          row.original.active ? 'bg-tertiary-container text-on-tertiary-container' : 'bg-error-container text-on-error-container'
        }`}>
          {row.original.active ? t('status_active') : t('status_inactive')}
        </span>
      ),
    },
    {
      id: 'mustChangePassword',
      header: t('col_must_change_password'),
      enableSorting: false,
      cell: ({ row }) => (
        row.original.mustChangePassword ? (
          <span className="text-xs flex items-center gap-1 text-on-surface-variant">
            <MaterialIcon name="warning" size={14} />
            {t('must_change_pw')}
          </span>
        ) : null
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
    <main className="p-6 space-y-6" aria-labelledby="users-title">
      <div className="flex items-center justify-between">
        <div>
          <h1 id="users-title" className="text-2xl font-semibold text-on-surface flex items-center gap-2">
            <MaterialIcon name="manage_accounts" className="text-primary" />
            {t('page_title')}
          </h1>
          <p className="text-sm text-on-surface-variant mt-1">{t('page_subtitle')}</p>
        </div>
        <M3Button icon="person_add" onClick={openCreate}>
          {t('btn_new_user')}
        </M3Button>
      </div>

      {loading ? (
        <M3LoadingState label={t('loading', { ns: 'common' })} />
      ) : users.length === 0 ? (
        <M3EmptyState icon="manage_accounts" title={t('no_users')} className="bg-surface rounded-shape-md shadow-elevation-1" />
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
    </main>
  );
}
