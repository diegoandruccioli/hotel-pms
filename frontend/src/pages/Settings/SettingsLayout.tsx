import { memo } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
import { PageHeader } from '../../components/PageHeader';
import { M3Card } from '../../components/m3';
import { getSettingsEntries } from '../../config/navigation';
import { useAuthStore } from '../../store';
import { cn } from '../../utils';

const linkClass = ({ isActive }: { isActive: boolean }) => cn(
  'flex shrink-0 items-center gap-3 whitespace-nowrap rounded-shape-full px-4 py-2.5 text-sm font-medium font-body transition-colors',
  'focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2',
  isActive
    ? 'bg-primary-container text-on-primary-container'
    : 'text-on-surface-variant hover:bg-surface-container-highest',
);

/** Settings area: page title, a side navigation of the sections the role may open
 * (a scrolling row on narrow screens) and the routed section next to it. */
export const SettingsLayout = memo(() => {
  const { t } = useTranslation('settings');
  const role = useAuthStore((s) => s.user?.role);
  const entries = getSettingsEntries(role);

  return (
    <div className="space-y-6 pb-10">
      <PageHeader icon="settings" title={t('settings')} subtitle={t('settings_subtitle')} />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[17.5rem_minmax(0,1fr)]">
        <M3Card variant="solid" className="h-fit p-2 lg:sticky lg:top-6">
          <nav aria-label={t('settings_nav_label')}>
            <ul className="flex gap-1 overflow-x-auto p-1 lg:flex-col lg:overflow-visible">
              {entries.map((entry) => (
                <li key={entry.id}>
                  <NavLink to={entry.path} className={linkClass}>
                    <MaterialIcon name={entry.icon} size={20} />
                    {t(entry.labelKey, { ns: entry.ns })}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
        </M3Card>

        <div className="min-w-0 space-y-6">
          <Outlet />
        </div>
      </div>
    </div>
  );
});

SettingsLayout.displayName = 'SettingsLayout';
