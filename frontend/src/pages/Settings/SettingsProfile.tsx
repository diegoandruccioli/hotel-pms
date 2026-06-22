import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../../store/authStore';
import { M3Card } from '../../components/m3/M3Card';
import { MaterialIcon } from '../../components/MaterialIcon';
import { SettingsPageHeader } from '../../components/SettingsPageHeader';

export const SettingsProfile = () => {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const { user } = useAuthStore();

  const handleBack = useCallback(() => navigate(-1), [navigate]);

  const roleLabel = user?.role ? t(`role_${user.role.toLowerCase()}`) : '';
  const userInitial = user?.username?.charAt(0).toUpperCase() ?? '?';

  return (
    <div className="space-y-6 max-w-2xl mx-auto pb-10">
      <SettingsPageHeader icon="person" title={t('my_profile')} subtitle={t('profile_subtitle')} onBack={handleBack} />

      <M3Card className="p-6">
        <div className="flex items-center gap-2 mb-5">
          <MaterialIcon name="person" className="text-primary" />
          <h2 className="text-lg font-medium text-on-surface">{t('section_account_info')}</h2>
        </div>
        <div className="flex items-center gap-4">
          <div
            className="flex items-center justify-center w-16 h-16 rounded-shape-full bg-primary text-on-primary text-2xl font-display font-bold"
            aria-hidden="true"
          >
            {userInitial}
          </div>
          <div>
            <p className="text-base font-semibold text-on-surface">{user?.username}</p>
            <p className="text-sm text-on-surface-variant capitalize">{roleLabel}</p>
          </div>
        </div>
      </M3Card>
    </div>
  );
};
