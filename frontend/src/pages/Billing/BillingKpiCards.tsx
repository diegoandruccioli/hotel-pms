import { useTranslation } from 'react-i18next';
import { M3StatCard } from '../../components/m3';
import { useFormatters } from '../../hooks';
import type { OwnerFinancialSummaryDto } from '../../types';

interface BillingKpiCardsProps {
  /** Totals for the current month: feeds "billed this month". */
  monthSummary: OwnerFinancialSummaryDto;
  /** Totals over the whole history: feeds "to collect", which must not depend on the month. */
  allTimeSummary: OwnerFinancialSummaryDto;
}

/** Billed this month and everything still to collect, above the invoice list.
 * "To collect" is the same number, with the same name, as on the dashboard, and links to
 * the issued invoices. Owner/admin only: the summary endpoint is role-gated server-side. */
export const BillingKpiCards = ({ monthSummary, allTimeSummary }: BillingKpiCardsProps) => {
  const { t } = useTranslation('common');
  const { formatCurrency } = useFormatters();

  return (
    <div data-testid="billing-kpi-grid" className="grid grid-cols-1 gap-5 sm:grid-cols-2">
      <M3StatCard
        label={t('billing_kpi_billed')}
        value={formatCurrency(monthSummary.totalRevenue)}
        icon="receipt_long"
        tone="info"
      />
      <M3StatCard
        label={t('stat_pending_revenue')}
        value={formatCurrency(allTimeSummary.pendingRevenue)}
        icon="hourglass_top"
        tone="warning"
        to="/billing?status=ISSUED"
      />
    </div>
  );
};
