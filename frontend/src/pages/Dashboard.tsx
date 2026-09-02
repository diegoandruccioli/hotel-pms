import { useEffect, useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Card } from '../components/m3/M3Card';
import { M3LoadingState } from '../components/m3/M3LoadingState';
import { M3ErrorState } from '../components/m3/M3ErrorState';
import { stayService } from '../services/stayService';
import { getErrorMessage } from '../utils/errorMessage';
import { useDaySheet, useOwnerFinancialSummary } from '../hooks/queries/useDashboard';
import type { RoomStatus } from '../types/inventory.types';
import type { AlloggiatiFailureSummaryResponse, CityTaxUnassessedSummaryResponse } from '../types/stay.types';

const ROOM_STATUS_COLORS: Record<RoomStatus, string> = {
  CLEAN:       'bg-tertiary-container/60 text-on-tertiary-container border-tertiary/50',
  DIRTY:       'bg-error-container/60 text-on-error-container border-error/50',
  MAINTENANCE: 'bg-secondary-container/60 text-on-secondary-container border-secondary/50',
  OCCUPIED:    'bg-primary-container/60 text-on-primary-container border-primary/50',
};

const ALL_ROOM_STATUSES: RoomStatus[] = ['CLEAN', 'DIRTY', 'MAINTENANCE', 'OCCUPIED'];

/** Owner financial summary covers the hotel's full operating history — same
 * range the pre-day-sheet Dashboard used for pending revenue — but now backed
 * by an aggregate-only query instead of downloading every invoice. */
const SUMMARY_START_DATE = '2000-01-01';
const SUMMARY_END_DATE = '2099-12-31';

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
  const isOwnerOrAdmin = user?.role === 'OWNER' || user?.role === 'ADMIN';

  const [alloggiatiFailures, setAlloggiatiFailures] = useState<AlloggiatiFailureSummaryResponse | null>(null);
  const [cityTaxUnassessed, setCityTaxUnassessed] = useState<CityTaxUnassessedSummaryResponse | null>(null);

  const { data: daySheet, isLoading, error: queryError, refetch } = useDaySheet();
  const { data: ownerSummary } = useOwnerFinancialSummary(SUMMARY_START_DATE, SUMMARY_END_DATE, isOwnerOrAdmin);
  const error = queryError ? getErrorMessage(queryError, t('failed_load_dashboard')) : null;
  const handleRetry = useCallback(() => { refetch(); }, [refetch]);

  useEffect(() => {
    if (!isOwnerOrAdmin) return;
    stayService.getAlloggiatiFailureSummary()
      .then(setAlloggiatiFailures)
      .catch(() => setAlloggiatiFailures(null));
    stayService.getCityTaxUnassessedSummary()
      .then(setCityTaxUnassessed)
      .catch(() => setCityTaxUnassessed(null));
  }, [isOwnerOrAdmin]);

  const universalStats = useMemo<StatCardConfig[]>(() => [
    {
      nameKey: 'stat_guests_in_house',
      stat: daySheet ? daySheet.guestsInHouse.toLocaleString() : '0',
      icon: 'group',
      containerClass: 'bg-primary-container text-on-primary-container',
      href: '/stays',
      state: { statusFilter: 'CHECKED_IN' },
    },
    {
      nameKey: 'stat_today_checkins',
      stat: daySheet ? daySheet.todayArrivals.toString() : '0',
      icon: 'login',
      containerClass: 'bg-tertiary-container text-on-tertiary-container',
      href: '/reservations',
      state: { upcomingOnly: true, sortField: 'checkInDate', sortDir: 'asc' },
    },
    {
      nameKey: 'stat_today_checkouts',
      stat: daySheet ? daySheet.todayDepartures.toString() : '0',
      icon: 'logout',
      containerClass: 'bg-secondary-container text-on-secondary-container',
      href: '/stays',
      state: { statusFilter: 'CHECKED_IN', sortField: 'expectedCheckOutDate', sortDir: 'asc' },
    },
    {
      nameKey: 'stat_available_rooms',
      stat: daySheet ? daySheet.availableRooms.toString() : '0',
      icon: 'meeting_room',
      containerClass: 'bg-surface-container-highest text-on-surface',
      href: '/rooms',
      state: { availableToday: true },
    },
  ], [daySheet]);

  const ownerStat = useMemo<StatCardConfig | null>(() => {
    if (!isOwnerOrAdmin || !ownerSummary) return null;
    return {
      nameKey: 'stat_pending_revenue',
      stat: new Intl.NumberFormat(i18n.language, { style: 'currency', currency: 'EUR' })
        .format(ownerSummary.pendingRevenue),
      icon: 'receipt_long',
      containerClass: 'bg-error-container text-on-error-container',
      href: '/billing',
    };
  }, [isOwnerOrAdmin, ownerSummary, i18n.language]);

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
          <MaterialIcon name="warning" size={20} className="shrink-0" />
          <div className="flex-1">
            <p className="text-sm font-body font-medium">{t('alloggiati_failure_banner_title')}</p>
            <p className="text-sm font-body">
              {t('alloggiati_failure_banner_desc', { count: alloggiatiFailures.failedCount })}
            </p>
          </div>
          <Link
            to="/stays"
            className="inline-flex items-center min-h-[40px] text-sm font-medium font-body underline hover:no-underline whitespace-nowrap focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-on-error-container rounded-sm"
          >
            {t('view_all')}
          </Link>
        </div>
      )}

      {cityTaxUnassessed && cityTaxUnassessed.unassessedCount > 0 && (
        <div
          role="alert"
          className="mt-4 flex items-center gap-3 px-4 py-3 rounded-shape-sm bg-error-container text-on-error-container"
        >
          <MaterialIcon name="warning" size={20} className="shrink-0" />
          <div className="flex-1">
            <p className="text-sm font-body font-medium">{t('city_tax_unassessed_banner_title')}</p>
            <p className="text-sm font-body">
              {t('city_tax_unassessed_banner_desc', { count: cityTaxUnassessed.unassessedCount })}
            </p>
          </div>
          <Link
            to="/settings/city-tax"
            className="inline-flex items-center min-h-[40px] text-sm font-medium font-body underline hover:no-underline whitespace-nowrap focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-on-error-container rounded-sm"
          >
            {t('city_tax_unassessed_banner_action')}
          </Link>
        </div>
      )}

      {isLoading ? (
        <M3LoadingState label={t('loading')} className="mt-8" />
      ) : error ? (
        <M3ErrorState
          title={t('error_loading_dashboard')}
          message={error}
          retryLabel={t('try_again')}
          onRetry={handleRetry}
          className="mt-8"
        />
      ) : (
        <div className="mt-8 space-y-6">
          <div data-testid="stats-grid" className={gridClass}>
            {allStats.map((item) => (
              <M3Card key={item.nameKey} variant="glass" className="overflow-hidden">
                <div className="p-5">
                  <div className="flex items-center">
                    <div className={`flex items-center justify-center w-12 h-12 rounded-shape-lg shrink-0 ${item.containerClass}`}>
                      <MaterialIcon name={item.icon} size={24} />
                    </div>
                    <div className="ml-4 flex-1 min-w-0">
                      <dl>
                        <dt className="text-sm font-body font-medium text-on-surface-variant truncate">{t(item.nameKey)}</dt>
                        <dd>
                          <div className="text-xl font-display font-bold text-on-surface">{item.stat}</div>
                        </dd>
                      </dl>
                    </div>
                  </div>
                </div>
                <div className="bg-surface-container-low px-5 py-3">
                  <Link
                    to={item.href}
                    state={item.state}
                    className="inline-flex items-center min-h-[40px] text-sm font-medium font-body text-primary hover:text-primary/80 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-sm"
                  >
                    {t('view_all')}
                  </Link>
                </div>
              </M3Card>
            ))}
          </div>

          {/* Room status overview — counts only; the day-sheet endpoint doesn't
              carry the full per-room list, see Housekeeping for that. */}
          {daySheet && (
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
                  className="inline-flex items-center min-h-[40px] text-sm font-medium font-body text-primary hover:text-primary/80 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-sm"
                >
                  {t('view_all')}
                </Link>
              </div>
              <div data-testid="room-status-summary" className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                {ALL_ROOM_STATUSES.map((status) => (
                  <div
                    key={status}
                    className={`rounded-shape-sm border p-3 text-center ${ROOM_STATUS_COLORS[status]}`}
                  >
                    <div className="text-xl font-display font-bold">
                      {daySheet.roomStatusCounts[status] ?? 0}
                    </div>
                    <div className="text-xs font-body font-medium uppercase tracking-wide">
                      {t(`room_status_${status.toLowerCase()}`)}
                    </div>
                  </div>
                ))}
              </div>
            </M3Card>
          )}
        </div>
      )}
    </div>
  );
};
