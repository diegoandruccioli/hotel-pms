import { useFormatters } from '../hooks';
import { useEffect, useCallback, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../components/MaterialIcon';
import { Alert } from '../components/Alert';
import { PageHeader } from '../components/PageHeader';
import { M3Button, M3Card, M3StatCard } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { stayService } from '../services';
import { getErrorMessage, cn, roomStatusTone, toneTileClasses, todayIsoDate } from '../utils';
import type { StatusTone } from '../utils';
import { useDaySheet, useDaySheetTrend, useOwnerFinancialSummary } from '../hooks/queries';
import type { RoomStatus } from '../types';
import type { AlloggiatiFailureSummaryResponse, CityTaxUnassessedSummaryResponse } from '../types';
import { ArrivalsDeparturesPanel } from './Dashboard/ArrivalsDeparturesPanel';
import { OwnerSummarySection } from './Dashboard/OwnerSummarySection';
import { kpiTrend } from './Dashboard/kpiTrend';
import type { TrendField } from './Dashboard/kpiTrend';

const ALL_ROOM_STATUSES: RoomStatus[] = ['CLEAN', 'DIRTY', 'MAINTENANCE', 'OCCUPIED'];

/** Owner financial summary covers the hotel's full operating history — same
 * range the pre-day-sheet Dashboard used for pending revenue — but now backed
 * by an aggregate-only query instead of downloading every invoice. */
const SUMMARY_START_DATE = '2000-01-01';
const SUMMARY_END_DATE = '2099-12-31';

const TREND_DAYS = 7;

interface StatCardConfig {
  nameKey: string;
  value: string;
  icon: string;
  tone: StatusTone;
  to: string;
  state?: Record<string, unknown>;
  /** Day-sheet field and live value feeding the sparkline and the delta; absent = no trend. */
  trend?: { field: TrendField; today: number };
}

/** Signed difference as text: the delta icon is decorative, so the sign must be in the words. */
const formatSigned = (value: number): string =>
  `${value > 0 ? '+' : value < 0 ? '−' : ''}${Math.abs(value)}`;

export const Dashboard = () => {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const { formatCurrency } = useFormatters();
  const user = useAuthStore((state) => state.user);
  const isOwnerOrAdmin = user?.role === 'OWNER' || user?.role === 'ADMIN';

  const [alloggiatiFailures, setAlloggiatiFailures] = useState<AlloggiatiFailureSummaryResponse | null>(null);
  const [cityTaxUnassessed, setCityTaxUnassessed] = useState<CityTaxUnassessedSummaryResponse | null>(null);

  const { data: daySheet, isLoading, error: queryError, refetch } = useDaySheet();
  const { data: trendData } = useDaySheetTrend(TREND_DAYS);
  const { data: ownerSummary } = useOwnerFinancialSummary(SUMMARY_START_DATE, SUMMARY_END_DATE, isOwnerOrAdmin);
  const error = queryError ? getErrorMessage(queryError, t('failed_load_dashboard')) : null;
  const handleRetry = useCallback(() => { refetch(); }, [refetch]);
  const handleNewReservation = useCallback(() => navigate('/reservations/new'), [navigate]);
  const handleWalkIn = useCallback(() => navigate('/stays/walk-in'), [navigate]);

  // Open to every role, not just OWNER/ADMIN (GAP-26 in THREAT_MODEL.md):
  // these drive alert banners reception needs to act on directly (missing
  // Alloggiati submissions, unassessed city tax), not just ownership.
  useEffect(() => {
    stayService.getAlloggiatiFailureSummary()
      .then(setAlloggiatiFailures)
      .catch(() => setAlloggiatiFailures(null));
    stayService.getCityTaxUnassessedSummary()
      .then(setCityTaxUnassessed)
      .catch(() => setCityTaxUnassessed(null));
  }, []);

  const universalStats = useMemo<StatCardConfig[]>(() => [
    {
      nameKey: 'stat_guests_in_house',
      value: daySheet ? daySheet.guestsInHouse.toLocaleString() : '0',
      icon: 'group',
      tone: 'info',
      to: '/stays',
      state: { statusFilter: 'CHECKED_IN' },
      trend: { field: 'guestsInHouse', today: daySheet?.guestsInHouse ?? 0 },
    },
    {
      nameKey: 'stat_today_checkins',
      value: daySheet ? daySheet.todayArrivals.toString() : '0',
      icon: 'login',
      tone: 'success',
      to: '/reservations',
      state: { upcomingOnly: true, sortField: 'checkInDate', sortDir: 'asc' },
      trend: { field: 'arrivals', today: daySheet?.todayArrivals ?? 0 },
    },
    {
      nameKey: 'stat_today_checkouts',
      value: daySheet ? daySheet.todayDepartures.toString() : '0',
      icon: 'logout',
      tone: 'warning',
      to: '/stays',
      state: { statusFilter: 'CHECKED_IN', sortField: 'expectedCheckOutDate', sortDir: 'asc' },
      trend: { field: 'departures', today: daySheet?.todayDepartures ?? 0 },
    },
    {
      nameKey: 'stat_available_rooms',
      value: daySheet ? daySheet.availableRooms.toString() : '0',
      icon: 'meeting_room',
      tone: 'neutral',
      to: '/rooms',
      state: { availableToday: true },
      trend: { field: 'availableRooms', today: daySheet?.availableRooms ?? 0 },
    },
  ], [daySheet]);

  const ownerStat = useMemo<StatCardConfig | null>(() => {
    if (!isOwnerOrAdmin || !ownerSummary) return null;
    return {
      nameKey: 'stat_pending_revenue',
      value: formatCurrency(ownerSummary.pendingRevenue),
      icon: 'receipt_long',
      tone: 'error',
      to: '/billing',
    };
  }, [isOwnerOrAdmin, ownerSummary, formatCurrency]);

  const allStats = useMemo<StatCardConfig[]>(
    () => (ownerStat ? [...universalStats, ownerStat] : universalStats),
    [universalStats, ownerStat],
  );

  const kpiCards = useMemo(() => {
    // Anchored to the day-sheet's own date so "yesterday" matches the live values, not the browser clock.
    const today = daySheet?.date ?? todayIsoDate();
    return allStats.map((item) => {
      const trend = item.trend
        ? kpiTrend(trendData?.points, item.trend.field, item.trend.today, today)
        : undefined;
      const series = trend && trend.values.length >= 2 ? trend.values : undefined;
      return {
        nameKey: item.nameKey,
        label: t(item.nameKey),
        value: item.value,
        icon: item.icon,
        tone: item.tone,
        to: item.to,
        state: item.state,
        trend: series,
        trendLabel: series
          ? t('dashboard_trend_label', { count: series.length, values: series.join(', ') })
          : undefined,
        // `percent` carries the signed difference (only its sign drives icon and tone); zero is left out, it would read as a rise.
        delta: !trend?.delta
          ? undefined
          : { percent: trend.delta, label: t('dashboard_delta_vs_yesterday', { value: formatSigned(trend.delta) }) },
      };
    });
  }, [allStats, daySheet, trendData, t]);

  const gridClass = allStats.length === 5
    ? 'grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-5'
    : 'grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4';

  return (
    <div data-testid="dashboard-page">
      <PageHeader
        title={`${t('welcome_back', { name: user?.username })} 👋`}
        subtitle={t('dashboard_subtitle')}
        titleTestId="dashboard-heading"
        actions={
          <div className="flex flex-wrap gap-2">
            <M3Button icon="add" onClick={handleNewReservation}>{t('new_reservation')}</M3Button>
            <M3Button icon="directions_walk" variant="tonal" onClick={handleWalkIn}>{t('dashboard_action_walk_in')}</M3Button>
          </div>
        }
      />

      {alloggiatiFailures && alloggiatiFailures.failedCount > 0 && (
        <Alert
          tone="error"
          icon="warning"
          title={t('alloggiati_failure_banner_title')}
          className="mt-4"
          action={
            <Link
              to="/stays"
              className="inline-flex items-center min-h-10 text-sm font-medium font-body underline hover:no-underline whitespace-nowrap focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-on-error-container rounded-sm"
            >
              {t('view_all')}
            </Link>
          }
        >
          {t('alloggiati_failure_banner_desc', { count: alloggiatiFailures.failedCount })}
        </Alert>
      )}

      {cityTaxUnassessed && cityTaxUnassessed.unassessedCount > 0 && (
        <Alert
          tone="error"
          icon="warning"
          title={t('city_tax_unassessed_banner_title')}
          className="mt-4"
          action={
            <Link
              to="/settings/city-tax"
              className="inline-flex items-center min-h-10 text-sm font-medium font-body underline hover:no-underline whitespace-nowrap focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-on-error-container rounded-sm"
            >
              {t('city_tax_unassessed_banner_action')}
            </Link>
          }
        >
          {t('city_tax_unassessed_banner_desc', { count: cityTaxUnassessed.unassessedCount })}
        </Alert>
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
            {kpiCards.map(({ nameKey, ...card }) => (
              <M3StatCard key={nameKey} {...card} />
            ))}
          </div>

          {/* Actionable front-desk work list — arrivals/departures for today,
              per the front-desk-dashboard convention (Cloudbeds "Today",
              Mews front-desk timeline): rows with inline actions, not just
              the counters above. Visible to every role. */}
          <ArrivalsDeparturesPanel />

          {/* Owner/admin-only "today at a glance" — occupancy/ADR/RevPAR,
              linking into the full comparative report at /owner-dashboard. */}
          {isOwnerOrAdmin && <OwnerSummarySection />}

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
                  className="inline-flex items-center min-h-10 text-sm font-medium font-body text-primary hover:text-primary/80 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-sm"
                >
                  {t('view_all')}
                </Link>
              </div>
              <div data-testid="room-status-summary" className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                {ALL_ROOM_STATUSES.map((status) => (
                  <div
                    key={status}
                    className={cn('rounded-shape-sm border p-3 text-center', toneTileClasses[roomStatusTone[status]])}
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
