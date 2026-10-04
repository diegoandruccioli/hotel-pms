import { useTranslation } from 'react-i18next';
import { M3StatCard } from '../../components/m3';
import { EMPTY_PLACEHOLDER, formatSigned } from '../../utils';
import type { OwnerFinancialReportDto, OwnerFinancialSummaryDto } from '../../types';

interface OwnerKpiCardsProps {
  report: OwnerFinancialReportDto;
  /** Same-length period right before the report's; absent when its best-effort load failed. */
  previousSummary: OwnerFinancialSummaryDto | null;
  formatCurrency: (amount: number) => string;
}

const PERCENT = 100;

/** Percentage change from `previous` to `current`, or `null` when there's no
 * previous-period baseline to compare against (avoids a nonsensical +∞%). */
const percentChange = (current: number, previous: number): number | null => {
  if (previous === 0) return current === 0 ? 0 : null;
  return ((current - previous) / Math.abs(previous)) * PERCENT;
};

/** Paid share of issued invoices, as a fraction; `null` with no invoices (nothing to collect). */
const collectionRate = (paid: number, total: number): number | null => (total > 0 ? paid / total : null);

/**
 * The report's four headline figures, each compared with the previous period
 * when one is available. A zero change is left out: the delta icon would read
 * it as a rise.
 */
export const OwnerKpiCards = ({ report, previousSummary, formatCurrency }: OwnerKpiCardsProps) => {
  const { t, i18n } = useTranslation('common');

  const countDelta = (current: number, previous: number | undefined) => {
    const change = previous === undefined ? null : percentChange(current, previous);
    const rounded = change === null ? 0 : Math.round(change);
    return rounded === 0
      ? undefined
      : { percent: rounded, label: t('delta_vs_previous_period', { value: `${formatSigned(rounded)}%` }) };
  };

  const rate = collectionRate(report.paidInvoices, report.totalInvoices);
  const previousRate = previousSummary
    ? collectionRate(previousSummary.paidInvoices, previousSummary.totalInvoices)
    : null;
  const rateChange = rate !== null && previousRate !== null ? Math.round(rate * PERCENT) - Math.round(previousRate * PERCENT) : 0;
  const rateDelta = rateChange === 0
    ? undefined
    : { percent: rateChange, label: t('delta_points_vs_previous_period', { value: formatSigned(rateChange) }) };
  const formattedRate = rate === null
    ? EMPTY_PLACEHOLDER
    : new Intl.NumberFormat(i18n.language, { style: 'percent', maximumFractionDigits: 0 }).format(rate);

  return (
    <div data-testid="owner-kpi-grid" className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-4">
      <M3StatCard
        label={t('total_revenue')}
        value={formatCurrency(report.totalRevenue)}
        icon="trending_up"
        tone="info"
        delta={countDelta(report.totalRevenue, previousSummary?.totalRevenue)}
      />
      <M3StatCard
        label={t('total_invoices')}
        value={String(report.totalInvoices)}
        icon="description"
        tone="neutral"
        delta={countDelta(report.totalInvoices, previousSummary?.totalInvoices)}
      />
      <M3StatCard
        label={t('paid_invoices')}
        value={String(report.paidInvoices)}
        icon="verified"
        tone="success"
        delta={countDelta(report.paidInvoices, previousSummary?.paidInvoices)}
      />
      <M3StatCard
        label={t('collection_rate_title')}
        value={formattedRate}
        icon="percent"
        tone="success"
        delta={rateDelta}
      />
    </div>
  );
};
