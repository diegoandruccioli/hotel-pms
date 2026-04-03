import { useEffect, useCallback, useMemo } from 'react';
import { useAuthStore } from '../store/authStore';
import { useDashboardStore } from '../store/dashboardStore';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Card } from '../components/m3/M3Card';

export const Dashboard = () => {
  const { t } = useTranslation('common');
  const user = useAuthStore((state) => state.user);
  const { stats, isLoading, error, fetchStats } = useDashboardStore();

  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  const handleRefresh = useCallback(() => {
    fetchStats();
  }, [fetchStats]);

  const statsConfig = useMemo(() => [
    {
      nameKey: 'stat_total_guests',
      stat: stats ? stats.totalGuests.toLocaleString() : '0',
      icon: 'group',
      containerClass: 'bg-primary-container text-on-primary-container',
    },
    {
      nameKey: 'stat_active_reservations',
      stat: stats ? `${stats.activeReservationsPercentage.toFixed(2)}%` : '0%',
      icon: 'event',
      containerClass: 'bg-tertiary-container text-on-tertiary-container',
    },
    {
      nameKey: 'stat_current_stays',
      stat: stats ? `${stats.currentStaysPercentage.toFixed(2)}%` : '0%',
      icon: 'hotel',
      containerClass: 'bg-secondary-container text-on-secondary-container',
    },
    {
      nameKey: 'stat_pending_revenue',
      stat: stats ? new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(stats.pendingRevenue) : '$0',
      icon: 'receipt_long',
      containerClass: 'bg-error-container text-on-error-container',
    },
  ], [stats]);

  return (
    <div data-testid="dashboard-page">
      <h1 data-testid="dashboard-heading" className="text-2xl font-display font-semibold text-on-surface">
        {t('welcome_back', { name: user?.username })} 👋
      </h1>
      <p className="mt-1 text-sm font-body text-on-surface-variant">
        {t('dashboard_subtitle')}
      </p>

      {error ? (
        <div className="mt-8 flex items-center gap-3 px-4 py-3 rounded-shape-sm bg-error-container text-on-error-container">
          <MaterialIcon name="error" size={20} className="flex-shrink-0" />
          <div>
            <p className="text-sm font-body">{t(error)}</p>
            <button onClick={handleRefresh} className="mt-1 text-sm font-medium underline hover:no-underline">
              {t('try_again')}
            </button>
          </div>
        </div>
      ) : (
        <div className="mt-8">
          <div data-testid="stats-grid" className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {statsConfig.map((item) => (
              <M3Card key={item.nameKey} variant="glass" className="overflow-hidden">
                <div className="p-5">
                  <div className="flex items-center">
                    <div className={`flex items-center justify-center w-12 h-12 rounded-shape-lg ${item.containerClass}`}>
                      <MaterialIcon name={item.icon} size={24} />
                    </div>
                    <div className="ml-4 flex-1">
                      <dl>
                        <dt className="text-sm font-body font-medium text-on-surface-variant truncate">{t(item.nameKey)}</dt>
                        <dd>
                          {isLoading ? (
                            <div className="h-7 bg-surface-container-highest rounded-shape-xs animate-pulse w-24 mt-1" />
                          ) : (
                            <div className="text-xl font-display font-bold text-on-surface">{item.stat}</div>
                          )}
                        </dd>
                      </dl>
                    </div>
                  </div>
                </div>
                <div className="bg-surface-container-low px-5 py-3">
                  <button type="button" className="text-sm font-medium font-body text-primary hover:text-primary/80" disabled={isLoading}>
                    {t('view_all')}
                  </button>
                </div>
              </M3Card>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
