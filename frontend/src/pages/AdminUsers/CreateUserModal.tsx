import { useState, useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { userService } from '../../services/userService';
import type { UserResponse, CreateUserRequest } from '../../types/user.types';
import { M3Button } from '../../components/m3/M3Button';
import { M3Dialog } from '../../components/m3/M3Dialog';
import { PasswordVisibilityToggle } from '../../components/m3/PasswordVisibilityToggle';
import { getErrorMessage } from '../../utils/errorMessage';
import type { Role } from '../../types/auth.types';

interface CreateUserModalProps {
  onClose: () => void;
  onCreated: (u: UserResponse) => void;
}

const INITIAL_FORM: CreateUserRequest = { username: '', password: '', email: '', role: 'RECEPTIONIST' };

export const CreateUserModal = memo(({ onClose, onCreated }: CreateUserModalProps) => {
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

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!form.username || !form.password || !form.email) {
      setError(t('err_all_fields_required'));
      return;
    }
    setLoading(true);
    try {
      const created = await userService.createUser(form);
      onCreated(created);
    } catch (err: unknown) {
      setError(getErrorMessage(err, t('err_create_failed')));
    } finally {
      setLoading(false);
    }
  }, [form, onCreated, t]);

  return (
    <M3Dialog
      open
      title={t('modal_create_title')}
      titleId="create-user-title"
      onClose={onClose}
      footer={
        <div className="flex justify-end gap-2">
          <M3Button variant="text" onClick={onClose} disabled={loading}>{t('btn_cancel')}</M3Button>
          <M3Button form="create-user-form" type="submit" loading={loading} disabled={loading}>{t('btn_create')}</M3Button>
        </div>
      }
    >
      <form id="create-user-form" onSubmit={handleSubmit} noValidate className="space-y-4">
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
      </form>
    </M3Dialog>
  );
});
CreateUserModal.displayName = 'CreateUserModal';
