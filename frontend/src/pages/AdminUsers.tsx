import { useState, useEffect, useCallback, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { userService } from '../services/userService';
import type { UserResponse } from '../types/user.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3Table } from '../components/m3/M3Table';
import { M3LoadingState } from '../components/m3/M3LoadingState';
import { M3EmptyState } from '../components/m3/M3EmptyState';
import { useToastStore } from '../store/toastStore';
import { useAuthStore } from '../store/authStore';
import { getErrorMessage } from '../utils/errorMessage';
import { CreateUserModal } from './AdminUsers/CreateUserModal';
import { ResetPasswordModal } from './AdminUsers/ResetPasswordModal';
import { UserRow } from './AdminUsers/UserRow';

export function AdminUsers() {
  const { t } = useTranslation('admin');
  const { addToast } = useToastStore();
  const currentUser = useAuthStore((s) => s.user);
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [resetTarget, setResetTarget] = useState<UserResponse | null>(null);

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

  const tableHeaders = useMemo(() => (
    ['col_username', 'col_email', 'col_role', 'col_status', 'col_must_change_password', 'col_actions'] as const
  ).map((col) => t(col)), [t]);

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
        <M3Table headers={tableHeaders}>
          {users.map((u) => (
            <UserRow
              key={u.id}
              user={u}
              onToggle={handleToggle}
              onResetPassword={openReset}
              currentUsername={currentUser?.username}
            />
          ))}
        </M3Table>
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
