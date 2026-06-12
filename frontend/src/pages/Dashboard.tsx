import { useEffect, useCallback, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { useDashboardStore } from '../store/dashboardStore';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Card } from '../components/m3/M3Card';

interface StatCardConfig {
  nameKey: string;
  stat: string;
  icon: string;
  containerClass: string;
  href: string;
}

export const Dashboard = () => {
  const { t, i18n } = useTranslation('common');
  const user = useAuthStore((state) => state.user);
  const { stats, isLoading, error, fetchStats } = useDashboardStore();

  const isOwnerOrAdmin = user?.role === 'OWNER' || user?.role === 'ADMIN';

  useEffect(() => {
    fetchStats(isOwnerOrAdmin);
  }, [fetchStats, isOwnerOrAdmin]);

  const handleRefresh = useCallback(() => {
    fetchStats(isOwnerOrAdmin);
  }, [fetchStats, isOwnerOrAdmin]);

  const universalStats = useMemo<StatCardConfig[]>(() => [
    {
      nameKey: 'stat_total_guests',
      stat: stats ? stats.totalGuests.toLocaleString() : '0',
      icon: 'group',
      containerClass: 'bg-primary-container text-on-primary-container',
      href: '/guests',
    },
    {
      nameKey: 'stat_today_checkins',
      stat: stats ? stats.todayArrivals.toString() : '0',
      icon: 'login',
      containerClass: 'bg-tertiary-container text-on-tertiary-container',
      href: '/reservations',
    },
    {
      nameKey: 'stat_today_checkouts',
      stat: stats ? stats.todayDepartures.toString() : '0',
      icon: 'logout',
      containerClass: 'bg-secondary-container text-on-secondary-container',
      href: '/stays',
    },
    {
      nameKey: 'stat_available_rooms',
      stat: stats ? stats.availableRooms.toString() : '0',
      icon: 'meeting_room',
      containerClass: 'bg-surface-container-highest text-on-surface',
      href: '/rooms',
    },
  ], [stats]);

  const ownerStat = useMemo<StatCardConfig | null>(() => {
    if (!isOwnerOrAdmin || stats?.pendingRevenue === null || stats?.pendingRevenue === undefined) return null;
    return {
      nameKey: 'stat_pending_revenue',
      stat: new Intl.NumberFormat(i18n.language, { style: 'currency', currency: 'EUR' })
        .format(stats.pendingRevenue),
      icon: 'receipt_long',
      containerClass: 'bg-error-container text-on-error-container',
      href: '/billing',
    };
  }, [isOwnerOrAdmin, stats, i18n.language]);

  const allStats = useMemo<StatCardConfig[]>(
    () => (ownerStat ? [...universalStats, ownerStat] : universalStats),
    [universalStats, ownerStat],
  );

  const gridClass = allStats.length === 5
    ? 'grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5'
    : 'grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4';

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
          <div data-testid="stats-grid" className={gridClass}>
            {allStats.map((item) => (
              <M3Card key={item.nameKey} variant="glass" className="overflow-hidden">
                <div className="p-5">
                  <div className="flex items-center">
                    <div className={`flex items-center justify-center w-12 h-12 rounded-shape-lg flex-shrink-0 ${item.containerClass}`}>
                      <MaterialIcon name={item.icon} size={24} />
                    </div>
                    <div className="ml-4 flex-1 min-w-0">
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
                  <Link
                    to={item.href}
                    className="text-sm font-medium font-body text-primary hover:text-primary/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded"
                  >
                    {t('view_all')}
                  </Link>
                </div>
              </M3Card>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
