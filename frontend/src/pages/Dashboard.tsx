import { useEffect, useCallback, useMemo, useState, memo } from 'react';
import { Link } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { useDashboardStore } from '../store/dashboardStore';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Card } from '../components/m3/M3Card';
import { stayService } from '../services/stayService';
import type { RoomResponse, RoomStatus } from '../types/inventory.types';
import type { AlloggiatiFailureSummaryResponse } from '../types/stay.types';

const ROOM_GRID_STYLE = { gridTemplateColumns: 'repeat(auto-fill, minmax(64px, 1fr))' } as const;

const ROOM_STATUS_COLORS: Record<RoomStatus, string> = {
  CLEAN:       'bg-tertiary-container/60 text-on-tertiary-container border-tertiary/50',
  DIRTY:       'bg-error-container/60 text-on-error-container border-error/50',
  MAINTENANCE: 'bg-secondary-container/60 text-on-secondary-container border-secondary/50',
  OCCUPIED:    'bg-primary-container/60 text-on-primary-container border-primary/50',
};

const RoomCell = memo(({ room, statusLabel }: { room: RoomResponse; statusLabel: string }) => (
  <div
    className={`border rounded-shape-sm p-2 text-center ${ROOM_STATUS_COLORS[room.status]}`}
    title={`${room.roomNumber} — ${statusLabel}`}
    aria-label={`${room.roomNumber} ${statusLabel}`}
  >
    <div className="text-sm font-display font-semibold leading-tight">{room.roomNumber}</div>
    <div className="text-[10px] font-body mt-0.5 truncate">{statusLabel}</div>
  </div>
));
RoomCell.displayName = 'RoomCell';

interface StatCardConfig {
  nameKey: string;
  stat: string;
  icon: string;
  containerClass: string;
  href: string;
  state?: Record<string, unknown>;
}

export const Dashboard = () => {
  const { t, i18n } = useTranslation('common');
  const user = useAuthStore((state) => state.user);
  const { stats, isLoading, error, fetchStats } = useDashboardStore();

  const isOwnerOrAdmin = user?.role === 'OWNER' || user?.role === 'ADMIN';

  const [alloggiatiFailures, setAlloggiatiFailures] = useState<AlloggiatiFailureSummaryResponse | null>(null);

  useEffect(() => {
    fetchStats(isOwnerOrAdmin);
  }, [fetchStats, isOwnerOrAdmin]);

  useEffect(() => {
    if (!isOwnerOrAdmin) return;
    stayService.getAlloggiatiFailureSummary()
      .then(setAlloggiatiFailures)
      .catch(() => setAlloggiatiFailures(null));
  }, [isOwnerOrAdmin]);

  const handleRefresh = useCallback(() => {
    fetchStats(isOwnerOrAdmin);
  }, [fetchStats, isOwnerOrAdmin]);

  const universalStats = useMemo<StatCardConfig[]>(() => [
    {
      nameKey: 'stat_guests_in_house',
      stat: stats ? stats.guestsInHouse.toLocaleString() : '0',
      icon: 'group',
      containerClass: 'bg-primary-container text-on-primary-container',
      href: '/stays',
      state: { statusFilter: 'CHECKED_IN' },
    },
    {
      nameKey: 'stat_today_checkins',
      stat: stats ? stats.todayArrivals.toString() : '0',
      icon: 'login',
      containerClass: 'bg-tertiary-container text-on-tertiary-container',
      href: '/reservations',
      state: { upcomingOnly: true, sortField: 'checkInDate', sortDir: 'asc' },
    },
    {
      nameKey: 'stat_today_checkouts',
      stat: stats ? stats.todayDepartures.toString() : '0',
      icon: 'logout',
      containerClass: 'bg-secondary-container text-on-secondary-container',
      href: '/stays',
      state: { statusFilter: 'CHECKED_IN', sortField: 'expectedCheckOutDate', sortDir: 'asc' },
    },
    {
      nameKey: 'stat_available_rooms',
      stat: stats ? stats.availableRooms.toString() : '0',
      icon: 'meeting_room',
      containerClass: 'bg-surface-container-highest text-on-surface',
      href: '/rooms',
      state: { availableToday: true },
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

      {alloggiatiFailures && alloggiatiFailures.failedCount > 0 && (
        <div
          role="alert"
          className="mt-4 flex items-center gap-3 px-4 py-3 rounded-shape-sm bg-error-container text-on-error-container"
        >
          <MaterialIcon name="warning" size={20} className="flex-shrink-0" />
          <div className="flex-1">
            <p className="text-sm font-body font-medium">{t('alloggiati_failure_banner_title')}</p>
            <p className="text-sm font-body">
              {t('alloggiati_failure_banner_desc', { count: alloggiatiFailures.failedCount })}
            </p>
          </div>
          <Link
            to="/stays"
            className="text-sm font-medium font-body underline hover:no-underline whitespace-nowrap focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-on-error-container rounded"
          >
            {t('view_all')}
          </Link>
        </div>
      )}

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
        <div className="mt-8 space-y-6">
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
                    state={item.state}
                    className="text-sm font-medium font-body text-primary hover:text-primary/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded"
                  >
                    {t('view_all')}
                  </Link>
                </div>
              </M3Card>
            ))}
          </div>

          {/* Room status overview grid */}
          {stats && stats.rooms.length > 0 && (
            <M3Card variant="outlined" className="p-5">
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                  <MaterialIcon name="grid_view" size={20} className="text-primary" />
                  <h2 className="text-sm font-display font-semibold text-on-surface">
                    {t('room_overview_title')}
                  </h2>
                </div>
                <Link
                  to="/housekeeping"
                  className="text-sm font-medium font-body text-primary hover:text-primary/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded"
                >
                  {t('view_all')}
                </Link>
              </div>
              <div
                data-testid="room-overview-grid"
                className="grid gap-2"
                style={ROOM_GRID_STYLE}
              >
                {stats.rooms.map((room) => (
                  <RoomCell
                    key={room.id}
                    room={room}
                    statusLabel={t(`room_status_${room.status.toLowerCase()}`)}
                  />
                ))}
              </div>
            </M3Card>
          )}
        </div>
      )}
    </div>
  );
};
