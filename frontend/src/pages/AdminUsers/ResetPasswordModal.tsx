import { useState, useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { userService } from '../../services/userService';
import type { UserResponse } from '../../types';
import { M3Button } from '../../components/m3/M3Button';
import { M3Dialog } from '../../components/m3/M3Dialog';
import { M3TextField } from '../../components/m3/M3TextField';
import { getErrorMessage } from '../../utils/errorMessage';

interface ResetPasswordModalProps {
  user: UserResponse;
  onClose: () => void;
  onSuccess: () => void;
}

const PW_REGEX = /^(?=.*[A-Z].*[A-Z])(?=.*[0-9].*[0-9])(?=.*[^A-Za-z0-9].*[^A-Za-z0-9]).{16,}$/;

export const ResetPasswordModal = memo(({ user, onClose, onSuccess }: ResetPasswordModalProps) => {
  const { t } = useTranslation('admin');
  const [newPw, setNewPw] = useState('');
  const [confirmPw, setConfirmPw] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleNewPw = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setNewPw(e.target.value), []);
  const handleConfirmPw = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setConfirmPw(e.target.value), []);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (newPw.length < 16) { setError(t('err_password_too_short')); return; }
    if (!PW_REGEX.test(newPw)) { setError(t('err_password_too_weak')); return; }
    if (newPw !== confirmPw) { setError(t('err_passwords_mismatch')); return; }
    setLoading(true);
    try {
      await userService.resetUserPassword(user.id, newPw);
      onSuccess();
    } catch (err: unknown) {
      setError(getErrorMessage(err, t('err_reset_failed')));
    } finally {
      setLoading(false);
    }
  }, [newPw, confirmPw, user.id, onSuccess, t]);

  return (
    <M3Dialog
      open
      title={t('modal_reset_title', { username: user.username })}
      titleId="reset-pw-title"
      onClose={onClose}
      footer={
        <div className="flex justify-end gap-2">
          <M3Button variant="text" onClick={onClose} disabled={loading}>{t('btn_cancel')}</M3Button>
          <M3Button form="reset-pw-form" type="submit" loading={loading} disabled={loading}>{t('btn_reset_password')}</M3Button>
        </div>
      }
    >
      <form id="reset-pw-form" onSubmit={handleSubmit} noValidate className="space-y-4">
        <M3TextField
          label={t('label_new_password')}
          type="password"
          value={newPw}
          onChange={handleNewPw}
        />
        <M3TextField
          label={t('label_confirm_password')}
          type="password"
          value={confirmPw}
          onChange={handleConfirmPw}
        />
        {error && <p role="alert" className="text-sm text-error">{error}</p>}
      </form>
    </M3Dialog>
  );
});
ResetPasswordModal.displayName = 'ResetPasswordModal';
