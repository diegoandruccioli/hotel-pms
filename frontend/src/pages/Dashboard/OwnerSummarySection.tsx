import { useFormatters } from '../../hooks';
import { todayIsoDate } from '../../utils';
import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { M3StatCard } from '../../components/m3';
import { useKpiReport } from '../../hooks/queries';

/**
 * Owner/admin-only summary strip — today's occupancy/ADR/RevPAR, the numbers
 * a hotel owner checks every morning (per the market-convention pace/flash
 * report pattern). The cards are not links: the single "view" link beside the
 * title leads into the full comparative report at `/owner-dashboard`.
 */
export const OwnerSummarySection = () => {
  const { t, i18n } = useTranslation('common');
  const today = useMemo(() => todayIsoDate(), []);
  const { data: kpiReport, isLoading } = useKpiReport(today, today, 'DAY', true);

  const { formatCurrency } = useFormatters();
  const formatPercent = (fraction: number) =>
    new Intl.NumberFormat(i18n.language, { style: 'percent', maximumFractionDigits: 0 }).format(fraction);

  if (isLoading || !kpiReport) return null;

  return (
    <section aria-labelledby="owner-summary-title">
      <div className="mb-4 flex items-center justify-between">
        <h2 id="owner-summary-title" className="text-sm font-display font-semibold text-on-surface">
          {t('dashboard_owner_summary_title')}
        </h2>
        <Link
          to="/owner-dashboard"
          aria-label={t('dashboard_view_all_owner_report')}
          className="inline-flex items-center min-h-10 text-sm font-medium font-body text-primary hover:text-primary/80 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-sm"
        >
          {t('view_all')}
        </Link>
      </div>
      <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
        <M3StatCard label={t('occupancy')} value={formatPercent(kpiReport.totals.occupancyRate)} icon="grid_view" tone="info" />
        <M3StatCard label={t('adr')} value={formatCurrency(kpiReport.totals.adr)} icon="sell" tone="neutral" />
        <M3StatCard label={t('revpar')} value={formatCurrency(kpiReport.totals.revpar)} icon="trending_up" tone="success" />
      </div>
    </section>
  );
};
