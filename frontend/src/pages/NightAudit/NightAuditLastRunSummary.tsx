import { useTranslation } from 'react-i18next';
import type { NightAuditRunResponse } from '../../types';
import { useFormatters } from '../../hooks';
import { Alert } from '../../components/Alert';
import { M3StatCard } from '../../components/m3';
import { cashTotal } from './nightAuditUtils';

interface NightAuditLastRunSummaryProps {
  /** A COMPLETED run: failed ones carry no figures. */
  run: NightAuditRunResponse;
}

const figure = (value: number | null) => (value === null ? '—' : String(value));

/** Headline figures of the most recent completed closing. */
export const NightAuditLastRunSummary = ({ run }: NightAuditLastRunSummaryProps) => {
  const { t } = useTranslation('common');
  const { formatCurrency, formatDate } = useFormatters();

  return (
    <section aria-labelledby="night-audit-last-run-title" className="space-y-3">
      <h2 id="night-audit-last-run-title" className="text-sm font-display font-semibold text-on-surface">
        {t('night_audit_last_run_title', { date: formatDate(run.businessDate) })}
      </h2>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <M3StatCard label={t('night_audit_arrivals')} value={figure(run.arrivals)} icon="login" tone="info" />
        <M3StatCard label={t('night_audit_departures')} value={figure(run.departures)} icon="logout" tone="info" />
        <M3StatCard label={t('night_audit_guests_in_house')} value={figure(run.guestsInHouse)} icon="group" tone="neutral" />
        <M3StatCard
          label={t('night_audit_cash_total')}
          value={formatCurrency(cashTotal(run))}
          icon="payments"
          tone={run.cashSummaryDegraded ? 'warning' : 'success'}
        />
      </div>
      {run.cashSummaryDegraded && (
        <Alert tone="warning" compact>{t('night_audit_cash_degraded')}</Alert>
      )}
    </section>
  );
};
