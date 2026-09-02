import { useState, useCallback, useMemo, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { userService } from '../../services/userService';
import type { UserResponse, CreateUserRequest } from '../../types';
import { M3Button } from '../../components/m3';
import { M3Dialog } from '../../components/m3';
import { M3TextField } from '../../components/m3';
import { M3Select } from '../../components/m3';
import { getErrorMessage } from '../../utils';
import type { Role } from '../../types';

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

  const roleOptions = useMemo(() => [
    { value: 'RECEPTIONIST', label: t('role_receptionist') },
    { value: 'OWNER', label: t('role_owner') },
    { value: 'ADMIN', label: t('role_admin') },
  ], [t]);

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
        <M3TextField
          label={t('label_username')}
          value={form.username}
          onChange={handleUsername}
        />
        <M3TextField
          label={t('label_email')}
          type="email"
          value={form.email}
          onChange={handleEmail}
        />
        <M3TextField
          label={t('label_password')}
          type="password"
          value={form.password}
          onChange={handlePassword}
        />
        <M3Select
          label={t('label_role')}
          options={roleOptions}
          value={form.role}
          onChange={handleRole}
        />

        {error && <p role="alert" className="text-sm text-error">{error}</p>}
      </form>
    </M3Dialog>
  );
});
CreateUserModal.displayName = 'CreateUserModal';
