import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Card } from '../../components/m3';
import { useKpiReport } from '../../hooks/queries';

const todayIsoDate = (): string => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

interface MiniStat {
  key: string;
  labelKey: string;
  value: string;
  icon: string;
}

/**
 * Owner/admin-only summary strip — today's occupancy/ADR/RevPAR, the numbers
 * a hotel owner checks every morning (per the market-convention pace/flash
 * report pattern), each linking into the full comparative report at
 * `/owner-dashboard` rather than duplicating it here.
 */
export const OwnerSummarySection = () => {
  const { t, i18n } = useTranslation('common');
  const today = useMemo(() => todayIsoDate(), []);
  const { data: kpiReport, isLoading } = useKpiReport(today, today, 'DAY', true);

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat(i18n.language, { style: 'currency', currency: 'EUR' }).format(amount);
  const formatPercent = (fraction: number) =>
    new Intl.NumberFormat(i18n.language, { style: 'percent', maximumFractionDigits: 0 }).format(fraction);

  if (isLoading || !kpiReport) return null;

  const stats: MiniStat[] = [
    { key: 'occupancy', labelKey: 'occupancy', value: formatPercent(kpiReport.totals.occupancyRate), icon: 'grid_view' },
    { key: 'adr', labelKey: 'adr', value: formatCurrency(kpiReport.totals.adr), icon: 'sell' },
    { key: 'revpar', labelKey: 'revpar', value: formatCurrency(kpiReport.totals.revpar), icon: 'trending_up' },
  ];

  return (
    <M3Card variant="outlined" className="p-5">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <MaterialIcon name="bar_chart" size={20} className="text-primary" />
          <h2 className="text-sm font-display font-semibold text-on-surface">
            {t('dashboard_owner_summary_title')}
          </h2>
        </div>
        <Link
          to="/owner-dashboard"
          className="inline-flex items-center min-h-10 text-sm font-medium font-body text-primary hover:text-primary/80 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-sm"
        >
          {t('view_all')}
        </Link>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        {stats.map((stat) => (
          <div key={stat.key} className="flex items-center gap-3 rounded-shape-sm border border-outline-variant p-3">
            <MaterialIcon name={stat.icon} size={20} className="text-on-surface-variant shrink-0" />
            <div className="min-w-0">
              <p className="text-xs font-body text-on-surface-variant">{t(stat.labelKey)}</p>
              <p className="text-lg font-display font-bold text-on-surface truncate">{stat.value}</p>
            </div>
          </div>
        ))}
      </div>
    </M3Card>
  );
};
