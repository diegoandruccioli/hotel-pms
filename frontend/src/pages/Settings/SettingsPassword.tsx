import { useState, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../../store/authStore';
import { useToastStore } from '../../store/toastStore';
import { authService } from '../../services/authService';
import { M3Card } from '../../components/m3/M3Card';
import { M3Button } from '../../components/m3/M3Button';
import { M3TextField } from '../../components/m3/M3TextField';
import { MaterialIcon } from '../../components/MaterialIcon';
import { SettingsPageHeader } from '../../components/SettingsPageHeader';
import { PasswordRequirementsChecklist } from '../../components/PasswordRequirementsChecklist';
import { isPasswordValid } from '../../utils/passwordPolicy';

export const SettingsPassword = () => {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const location = useLocation();
  const mustChangePassword = (location.state as { mustChangePassword?: boolean } | null)?.mustChangePassword === true;
  const { logout } = useAuthStore();
  const { addToast } = useToastStore();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleBack = useCallback(() => navigate('/settings'), [navigate]);

  const handleCurrentPasswordChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => setCurrentPassword(e.target.value),
    []
  );
  const handleNewPasswordChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => setNewPassword(e.target.value),
    []
  );
  const handleConfirmPasswordChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => setConfirmPassword(e.target.value),
    []
  );

  const handleChangePassword = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (newPassword !== confirmPassword) {
      setError(t('passwords_dont_match'));
      return;
    }
    if (!isPasswordValid(newPassword)) {
      setError(t('password_requirements_not_met'));
      return;
    }

    try {
      setLoading(true);
      await authService.changePassword({ currentPassword, newPassword });
      addToast(t('password_changed_success'), 'success');
      logout();
      navigate('/login');
    } catch (err: unknown) {
      const e = err as { response?: { data?: { detail?: string } }; message?: string };
      setError(e.response?.data?.detail || t('password_change_failed'));
    } finally {
      setLoading(false);
    }
  }, [currentPassword, newPassword, confirmPassword, t, addToast, logout, navigate]);

  return (
    <div className="space-y-6 max-w-2xl mx-auto pb-10">
      <SettingsPageHeader icon="lock" title={t('section_change_password')} onBack={handleBack} />

      {mustChangePassword && (
        <div role="alert" className="flex items-start gap-3 rounded-2xl bg-warning-container text-on-warning-container px-4 py-3 text-sm font-medium">
          <MaterialIcon name="warning" size={18} className="mt-0.5 flex-shrink-0" />
          {t('must_change_password_banner', 'You must change your password before continuing.')}
        </div>
      )}

      <M3Card className="p-6">
        {error && (
          <div
            role="alert"
            className="mb-4 p-4 bg-error-container text-on-error-container rounded-shape-sm flex items-start gap-3"
          >
            <MaterialIcon name="error" />
            <p className="text-sm font-body mt-0.5">{error}</p>
          </div>
        )}

        <form onSubmit={handleChangePassword} className="space-y-4" noValidate>
          <M3TextField
            label={t('current_password')}
            type="password"
            value={currentPassword}
            onChange={handleCurrentPasswordChange}
            required
            autoComplete="current-password"
          />
          <M3TextField
            label={t('new_password')}
            type="password"
            value={newPassword}
            onChange={handleNewPasswordChange}
            required
            autoComplete="new-password"
          />
          <M3TextField
            label={t('confirm_new_password')}
            type="password"
            value={confirmPassword}
            onChange={handleConfirmPasswordChange}
            required
            autoComplete="new-password"
          />
          <PasswordRequirementsChecklist password={newPassword} />
          <div className="flex justify-end pt-2">
            <M3Button
              type="submit"
              loading={loading}
              disabled={!currentPassword || !newPassword || !confirmPassword}
            >
              {t('change_password')}
            </M3Button>
          </div>
        </form>
      </M3Card>
    </div>
  );
};
