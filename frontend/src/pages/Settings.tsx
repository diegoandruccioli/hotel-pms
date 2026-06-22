import { useCallback, useMemo, memo } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../store/authStore';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Card } from '../components/m3/M3Card';
import { SettingsPageHeader } from '../components/SettingsPageHeader';

interface SettingsHubItem {
  to: string;
  icon: string;
  titleKey: string;
  descKey: string;
}

const SETTINGS_ITEMS: SettingsHubItem[] = [
  { to: '/settings/profile', icon: 'person', titleKey: 'my_profile', descKey: 'settings_hub_profile_desc' },
  { to: '/settings/password', icon: 'lock', titleKey: 'change_password', descKey: 'settings_hub_password_desc' },
  { to: '/settings/accessibility', icon: 'accessibility_new', titleKey: 'settings_section_accessibility', descKey: 'settings_hub_accessibility_desc' },
  { to: '/settings/appearance', icon: 'palette', titleKey: 'settings_appearance_language_title', descKey: 'settings_hub_appearance_desc' },
];

const SYSTEM_ITEM: SettingsHubItem = {
  to: '/settings/system', icon: 'admin_panel_settings', titleKey: 'settings_section_system', descKey: 'settings_hub_system_desc',
};

const SettingsHubRow = memo(({ item }: { item: SettingsHubItem }) => {
  const { t } = useTranslation('settings');
  return (
    <Link
      to={item.to}
      className="flex items-center gap-4 px-4 py-4 rounded-[12px] hover:bg-surface-container-highest focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 transition-colors"
    >
      <span className="flex items-center justify-center w-10 h-10 rounded-shape-full bg-primary-container text-on-primary-container flex-shrink-0">
        <MaterialIcon name={item.icon} size={20} />
      </span>
      <span className="flex-1">
        <p className="text-sm font-medium text-on-surface">{t(item.titleKey)}</p>
        <p className="text-xs text-on-surface-variant mt-0.5">{t(item.descKey)}</p>
      </span>
      <MaterialIcon name="chevron_right" size={20} className="text-on-surface-variant flex-shrink-0" />
    </Link>
  );
});
SettingsHubRow.displayName = 'SettingsHubRow';

export const Settings = () => {
  const { t } = useTranslation('settings');
  const navigate = useNavigate();
  const role = useAuthStore((s) => s.user?.role);
  const isAdminOrOwner = role === 'ADMIN' || role === 'OWNER';

  const handleBack = useCallback(() => navigate(-1), [navigate]);

  const items = useMemo(
    () => (isAdminOrOwner ? [...SETTINGS_ITEMS, SYSTEM_ITEM] : SETTINGS_ITEMS),
    [isAdminOrOwner]
  );

  return (
    <div className="space-y-6 max-w-2xl mx-auto pb-10">
      <SettingsPageHeader icon="settings" title={t('settings')} subtitle={t('settings_subtitle')} onBack={handleBack} />
      <M3Card className="p-2">
        <div className="flex flex-col divide-y divide-outline-variant/30">
          {items.map((item) => (
            <SettingsHubRow key={item.to} item={item} />
          ))}
        </div>
      </M3Card>
    </div>
  );
};
