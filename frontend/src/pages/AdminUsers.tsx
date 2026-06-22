import { useState, useEffect, useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { userService } from '../services/userService';
import type { UserResponse, CreateUserRequest } from '../types/user.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { PasswordVisibilityToggle } from '../components/m3/PasswordVisibilityToggle';
import { useToastStore } from '../store/toastStore';
import { useAuthStore } from '../store/authStore';
import type { Role } from '../types/auth.types';

// -----------------------------------------------------------------------
// CreateUserModal
// -----------------------------------------------------------------------

interface CreateUserModalProps {
  onClose: () => void;
  onCreated: (u: UserResponse) => void;
}

const INITIAL_FORM: CreateUserRequest = { username: '', password: '', email: '', role: 'RECEPTIONIST' };

const CreateUserModal = memo(({ onClose, onCreated }: CreateUserModalProps) => {
  const { t } = useTranslation('admin');
  const [form, setForm] = useState<CreateUserRequest>(INITIAL_FORM);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const toggleShowPassword = useCallback(() => setShowPassword((prev) => !prev), []);

  const handleUsername = useCallback((e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((p) => ({ ...p, username: e.target.value })), []);
  const handleEmail = useCallback((e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((p) => ({ ...p, email: e.target.value })), []);
  const handlePassword = useCallback((e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((p) => ({ ...p, password: e.target.value })), []);
  const handleRole = useCallback((e: React.ChangeEvent<HTMLSelectElement>) =>
    setForm((p) => ({ ...p, role: e.target.value as Role })), []);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Escape') onClose();
  }, [onClose]);

  const handleSubmit = useCallback(async () => {
    setError('');
    if (!form.username || !form.password || !form.email) {
      setError(t('err_all_fields_required'));
      return;
    }
    setLoading(true);
    try {
      const created = await userService.createUser(form);
      onCreated(created);
    } catch {
      setError(t('err_create_failed'));
    } finally {
      setLoading(false);
    }
  }, [form, onCreated, t]);

  return (
    <dialog
      open
      aria-labelledby="create-user-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-0 border-0 max-w-none w-full h-full"
    >
      {/* eslint-disable-next-line jsx-a11y/no-static-element-interactions */}
      <div className="bg-surface rounded-2xl shadow-elevation-3 w-full max-w-md p-6 space-y-4"
        onKeyDown={handleKeyDown}>
        <h2 id="create-user-title" className="text-lg font-semibold text-on-surface">
          {t('modal_create_title')}
        </h2>

        <div>
          <label htmlFor="new-username" className="block text-sm font-medium text-on-surface mb-1">
            {t('label_username')}
          </label>
          <input id="new-username" type="text" value={form.username} onChange={handleUsername}
            className="w-full rounded-md border border-outline bg-surface px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary" />
        </div>
        <div>
          <label htmlFor="new-email" className="block text-sm font-medium text-on-surface mb-1">
            {t('label_email')}
          </label>
          <input id="new-email" type="email" value={form.email} onChange={handleEmail}
            className="w-full rounded-md border border-outline bg-surface px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary" />
        </div>
        <div>
          <label htmlFor="new-password" className="block text-sm font-medium text-on-surface mb-1">
            {t('label_password')}
          </label>
          <div className="relative">
            <input id="new-password" type={showPassword ? 'text' : 'password'} value={form.password} onChange={handlePassword}
              className="w-full rounded-md border border-outline bg-surface px-3 py-2 pr-12 text-sm focus:outline-none focus:ring-2 focus:ring-primary" />
            <PasswordVisibilityToggle
              visible={showPassword}
              onToggle={toggleShowPassword}
              className="absolute right-1 top-1/2 -translate-y-1/2"
            />
          </div>
        </div>
        <div>
          <label htmlFor="new-role" className="block text-sm font-medium text-on-surface mb-1">
            {t('label_role')}
          </label>
          <select id="new-role" value={form.role} onChange={handleRole}
            className="w-full rounded-md border border-outline bg-surface px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary">
            <option value="RECEPTIONIST">{t('role_receptionist')}</option>
            <option value="OWNER">{t('role_owner')}</option>
            <option value="ADMIN">{t('role_admin')}</option>
          </select>
        </div>

        {error && <p role="alert" className="text-sm text-error">{error}</p>}

        <div className="flex justify-end gap-3 pt-2">
          <button type="button" onClick={onClose}
            className="rounded-full border border-outline px-5 py-2 text-sm font-medium text-on-surface hover:bg-surface-variant focus:outline-none focus:ring-2 focus:ring-primary">
            {t('btn_cancel')}
          </button>
          <button type="button" onClick={handleSubmit} disabled={loading}
            className="rounded-full bg-primary px-5 py-2 text-sm font-medium text-on-primary hover:bg-primary/90 disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-primary">
            {loading ? t('btn_saving') : t('btn_create')}
          </button>
        </div>
      </div>
    </dialog>
  );
});
CreateUserModal.displayName = 'CreateUserModal';

// -----------------------------------------------------------------------
// ResetPasswordModal
// -----------------------------------------------------------------------

interface ResetPasswordModalProps {
  user: UserResponse;
  onClose: () => void;
  onSuccess: () => void;
}

const PW_REGEX = /^(?=.*[A-Z].*[A-Z])(?=.*[0-9].*[0-9])(?=.*[^A-Za-z0-9].*[^A-Za-z0-9]).{16,}$/;

const ResetPasswordModal = memo(({ user, onClose, onSuccess }: ResetPasswordModalProps) => {
  const { t } = useTranslation('admin');
  const [newPw, setNewPw] = useState('');
  const [confirmPw, setConfirmPw] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showNewPw, setShowNewPw] = useState(false);
  const [showConfirmPw, setShowConfirmPw] = useState(false);

  const handleNewPw = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setNewPw(e.target.value), []);
  const handleConfirmPw = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setConfirmPw(e.target.value), []);
  const toggleShowNewPw = useCallback(() => setShowNewPw((prev) => !prev), []);
  const toggleShowConfirmPw = useCallback(() => setShowConfirmPw((prev) => !prev), []);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Escape') onClose();
  }, [onClose]);

  const handleSubmit = useCallback(async () => {
    setError('');
    if (newPw.length < 16) { setError(t('err_password_too_short')); return; }
    if (!PW_REGEX.test(newPw)) { setError(t('err_password_too_weak')); return; }
    if (newPw !== confirmPw) { setError(t('err_passwords_mismatch')); return; }
    setLoading(true);
    try {
      await userService.resetUserPassword(user.id, newPw);
      onSuccess();
    } catch {
      setError(t('err_reset_failed'));
    } finally {
      setLoading(false);
    }
  }, [newPw, confirmPw, user.id, onSuccess, t]);

  return (
    <dialog
      open
      aria-labelledby="reset-pw-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-0 border-0 max-w-none w-full h-full"
    >
      {/* eslint-disable-next-line jsx-a11y/no-static-element-interactions */}
      <div className="bg-surface rounded-2xl shadow-elevation-3 w-full max-w-md p-6 space-y-4"
        onKeyDown={handleKeyDown}>
        <h2 id="reset-pw-title" className="text-lg font-semibold text-on-surface">
          {t('modal_reset_title', { username: user.username })}
        </h2>
        <div>
          <label htmlFor="reset-new-pw" className="block text-sm font-medium text-on-surface mb-1">
            {t('label_new_password')}
          </label>
          <div className="relative">
            <input id="reset-new-pw" type={showNewPw ? 'text' : 'password'} value={newPw} onChange={handleNewPw}
              className="w-full rounded-md border border-outline bg-surface px-3 py-2 pr-12 text-sm focus:outline-none focus:ring-2 focus:ring-primary" />
            <PasswordVisibilityToggle
              visible={showNewPw}
              onToggle={toggleShowNewPw}
              className="absolute right-1 top-1/2 -translate-y-1/2"
            />
          </div>
        </div>
        <div>
          <label htmlFor="reset-confirm-pw" className="block text-sm font-medium text-on-surface mb-1">
            {t('label_confirm_password')}
          </label>
          <div className="relative">
            <input id="reset-confirm-pw" type={showConfirmPw ? 'text' : 'password'} value={confirmPw} onChange={handleConfirmPw}
              className="w-full rounded-md border border-outline bg-surface px-3 py-2 pr-12 text-sm focus:outline-none focus:ring-2 focus:ring-primary" />
            <PasswordVisibilityToggle
              visible={showConfirmPw}
              onToggle={toggleShowConfirmPw}
              className="absolute right-1 top-1/2 -translate-y-1/2"
            />
          </div>
        </div>
        {error && <p role="alert" className="text-sm text-error">{error}</p>}
        <div className="flex justify-end gap-3 pt-2">
          <button type="button" onClick={onClose}
            className="rounded-full border border-outline px-5 py-2 text-sm font-medium text-on-surface hover:bg-surface-variant focus:outline-none focus:ring-2 focus:ring-primary">
            {t('btn_cancel')}
          </button>
          <button type="button" onClick={handleSubmit} disabled={loading}
            className="rounded-full bg-primary px-5 py-2 text-sm font-medium text-on-primary hover:bg-primary/90 disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-primary">
            {loading ? t('btn_saving') : t('btn_reset_password')}
          </button>
        </div>
      </div>
    </dialog>
  );
});
ResetPasswordModal.displayName = 'ResetPasswordModal';

// -----------------------------------------------------------------------
// UserRow
// -----------------------------------------------------------------------

interface UserRowProps {
  user: UserResponse;
  onToggle: (u: UserResponse) => void;
  onResetPassword: (u: UserResponse) => void;
  currentUsername: string | undefined;
}

const UserRow = memo(({ user, onToggle, onResetPassword, currentUsername }: UserRowProps) => {
  const { t } = useTranslation('admin');
  const handleToggle = useCallback(() => onToggle(user), [onToggle, user]);
  const handleReset = useCallback(() => onResetPassword(user), [onResetPassword, user]);

  return (
    <tr className="hover:bg-surface-variant/40 transition-colors">
      <td className="px-4 py-3 font-medium">{user.username}</td>
      <td className="px-4 py-3 text-on-surface-variant">{user.email}</td>
      <td className="px-4 py-3">
        <span className="rounded-full bg-secondary-container text-on-secondary-container px-2 py-0.5 text-xs font-medium">
          {user.role}
        </span>
      </td>
      <td className="px-4 py-3">
        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${
          user.active ? 'bg-tertiary-container text-on-tertiary-container' : 'bg-error-container text-on-error-container'
        }`}>
          {user.active ? t('status_active') : t('status_inactive')}
        </span>
      </td>
      <td className="px-4 py-3">
        {user.mustChangePassword && (
          <span className="text-xs flex items-center gap-1 text-on-surface-variant">
            <MaterialIcon name="warning" size={14} />
            {t('must_change_pw')}
          </span>
        )}
      </td>
      <td className="px-4 py-3">
        <div className="flex items-center gap-2">
          <button type="button" onClick={handleToggle}
            className="text-xs rounded-full border border-outline px-3 py-1 hover:bg-surface-variant focus:outline-none focus:ring-2 focus:ring-primary"
            aria-label={user.active ? t('btn_deactivate') : t('btn_activate')}>
            {user.active ? t('btn_deactivate') : t('btn_activate')}
          </button>
          {user.username !== currentUsername && (
            <button type="button" onClick={handleReset}
              className="text-xs rounded-full border border-outline px-3 py-1 hover:bg-surface-variant focus:outline-none focus:ring-2 focus:ring-primary"
              aria-label={`${t('btn_reset_password')} ${user.username}`}>
              {t('btn_reset_password')}
            </button>
          )}
        </div>
      </td>
    </tr>
  );
});
UserRow.displayName = 'UserRow';

// -----------------------------------------------------------------------
// Main page
// -----------------------------------------------------------------------

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
      } catch {
        addToast(t('err_toggle_failed'), 'error');
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
        <div className="flex justify-center py-16">
          <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
        </div>
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-outline-variant">
          <table className="w-full text-sm text-on-surface">
            <thead className="bg-surface-variant text-on-surface-variant uppercase text-xs tracking-wide">
              <tr>
                {(['col_username', 'col_email', 'col_role', 'col_status', 'col_must_change_password', 'col_actions'] as const).map((col) => (
                  <th key={col} scope="col" className="px-4 py-3 text-left font-medium">
                    {t(col)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant">
              {users.map((u) => (
                <UserRow
                  key={u.id}
                  user={u}
                  onToggle={handleToggle}
                  onResetPassword={openReset}
                  currentUsername={currentUser?.username}
                />
              ))}
            </tbody>
          </table>
          {users.length === 0 && (
            <p className="text-center py-8 text-on-surface-variant text-sm">{t('no_users')}</p>
          )}
        </div>
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
