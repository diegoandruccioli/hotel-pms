import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../../store';
import { M3Avatar, M3Card } from '../../components/m3';
import { MaterialIcon } from '../../components/MaterialIcon';
import { SettingsSectionTitle } from '../../components/SettingsSectionTitle';

export const SettingsProfile = () => {
  const { t } = useTranslation('common');
  const { user } = useAuthStore();


  const roleLabel = user?.role ? t(`role_${user.role.toLowerCase()}`) : '';

  return (
    <div className="space-y-6">
      <SettingsSectionTitle title={t('my_profile')} subtitle={t('profile_subtitle')} />

      <M3Card variant="solid" className="p-6">
        <div className="flex items-center gap-2 mb-5">
          <MaterialIcon name="person" className="text-primary" />
          <h2 className="text-lg font-medium text-on-surface">{t('section_account_info')}</h2>
        </div>
        <div className="flex items-center gap-4">
          <M3Avatar name={user?.username} size="lg" aria-hidden="true" />
          <div>
            <p className="text-base font-semibold text-on-surface">{user?.username}</p>
            <p className="text-sm text-on-surface-variant capitalize">{roleLabel}</p>
          </div>
        </div>
      </M3Card>
    </div>
  );
};
