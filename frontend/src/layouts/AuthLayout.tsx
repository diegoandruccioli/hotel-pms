import { Outlet } from 'react-router-dom';
import { MaterialIcon } from '../components/MaterialIcon';
import { useTranslation } from 'react-i18next';

export const AuthLayout = () => {
  const { t } = useTranslation('auth');

  return (
    <div className="min-h-screen bg-gradient-to-br from-surface via-primary-container/30 to-surface flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="flex justify-center">
          <div className="bg-primary p-3 rounded-shape-xl shadow-elevation-2">
            <MaterialIcon name="apartment" size={40} className="text-on-primary" />
          </div>
        </div>
        <h2 className="mt-6 text-center text-3xl font-display font-extrabold text-on-surface">
          Hotel PMS
        </h2>
        <p className="mt-2 text-center text-sm font-body text-on-surface-variant">
          {t('property_management_system')}
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div className="glass-surface-elevated py-8 px-4 shadow-elevation-2 rounded-shape-lg sm:px-10">
          <Outlet />
        </div>
      </div>
    </div>
  );
};
