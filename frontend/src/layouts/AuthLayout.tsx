import { Outlet } from 'react-router-dom';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Card } from '../components/m3/M3Card';
import { useTranslation } from 'react-i18next';
import { AuthPreferences } from './AuthPreferences';

const BRAND_FEATURES = [
  { icon: 'dashboard', key: 'brand_feature_1' },
  { icon: 'receipt_long', key: 'brand_feature_2' },
  { icon: 'contrast', key: 'brand_feature_3' },
] as const;

export const AuthLayout = () => {
  const { t } = useTranslation('auth');
  const { t: tc } = useTranslation('common');

  return (
    <div className="min-h-screen bg-surface-container-low lg:grid lg:grid-cols-[2fr_3fr]">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:px-4 focus:py-2 focus:rounded-shape-full focus:bg-primary focus:text-on-primary focus:ring-2 focus:ring-primary focus:ring-offset-2"
      >
        {tc('skip_to_main')}
      </a>

      {/* Brand panel: wide screens only. */}
      <aside className="hidden flex-col justify-between bg-primary-container p-12 text-on-primary-container lg:flex">
        <div className="flex items-center gap-3">
          <div className="rounded-shape-lg bg-primary p-2.5">
            <MaterialIcon name="apartment" size={28} className="text-on-primary" />
          </div>
          <span className="text-xl font-display font-bold">Hotel PMS</span>
        </div>

        <div className="space-y-6">
          <p className="text-4xl font-display font-bold leading-tight">{t('brand_headline')}</p>
          <p className="max-w-md text-base font-body">{t('brand_lead')}</p>
          <ul className="space-y-3">
            {BRAND_FEATURES.map(({ icon, key }) => (
              <li key={key} className="flex items-center gap-3 text-sm font-body">
                <MaterialIcon name={icon} size={20} />
                {t(key)}
              </li>
            ))}
          </ul>
        </div>

        <p className="text-xs font-body">{t('brand_footer', { year: new Date().getFullYear() })}</p>
      </aside>

      <div className="flex flex-col justify-center px-4 py-12 sm:px-6 lg:px-12">
        {/* Compact header: narrow screens only, where the brand panel is hidden. */}
        <div className="sm:mx-auto sm:w-full sm:max-w-md lg:hidden">
          <div className="flex justify-center">
            <div className="bg-primary p-3 rounded-shape-xl shadow-elevation-2">
              <MaterialIcon name="apartment" size={40} className="text-on-primary" />
            </div>
          </div>
          <p className="mt-6 text-center text-3xl font-display font-extrabold text-on-surface">Hotel PMS</p>
        </div>
        <p className="mt-2 text-center text-sm font-body text-on-surface-variant lg:hidden">
          {t('property_management_system')}
        </p>

        <main id="main-content" className="mx-auto mt-8 w-full max-w-md lg:mt-0" tabIndex={-1}>
          <M3Card variant="solid" className="py-8 px-4 sm:px-10">
            <Outlet />
          </M3Card>
          <AuthPreferences />
        </main>
      </div>
    </div>
  );
};
