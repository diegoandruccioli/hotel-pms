import { useTranslation } from 'react-i18next';
import { M3StatCard } from '../../components/m3';
import { useFormatters } from '../../hooks';
import type { OwnerFinancialSummaryDto } from '../../types';

interface BillingKpiCardsProps {
  summary: OwnerFinancialSummaryDto;
}

/** Month-to-date billed and still-to-collect totals, above the invoice list.
 * Owner/admin only: the summary endpoint is role-gated server-side. */
export const BillingKpiCards = ({ summary }: BillingKpiCardsProps) => {
  const { t } = useTranslation('common');
  const { formatCurrency } = useFormatters();

  return (
    <div data-testid="billing-kpi-grid" className="grid grid-cols-1 gap-5 sm:grid-cols-2">
      <M3StatCard
        label={t('billing_kpi_billed')}
        value={formatCurrency(summary.totalRevenue)}
        icon="receipt_long"
        tone="info"
      />
      <M3StatCard
        label={t('billing_kpi_outstanding')}
        value={formatCurrency(summary.pendingRevenue)}
        icon="hourglass_top"
        tone="warning"
      />
    </div>
  );
};
