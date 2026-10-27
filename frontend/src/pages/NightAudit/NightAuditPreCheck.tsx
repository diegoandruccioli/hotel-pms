import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Alert } from '../../components/Alert';
import { useStaysSearch } from '../../hooks/queries';

const PRE_CHECK_STAYS_SAMPLE_SIZE = 100;
/** In-house guests, soonest check-out first, so the overdue ones the warning counts head the list. */
const OVERDUE_DEPARTURES_STATE = { statusFilter: 'CHECKED_IN', sortField: 'expectedCheckOutDate', sortDir: 'asc' };

/**
 * Departures still open on the selected business date, per the OPERA End of Day
 * convention (surfaced before the close, not discovered after). Informational only —
 * the run stays enabled either way. Pending arrivals have their own step, see
 * `NoShowCandidatesList`.
 */
export const NightAuditPreCheck = ({ businessDate }: { businessDate: string }) => {
  const { t } = useTranslation('common');

  const { data: openStaysPage } = useStaysSearch({
    status: 'CHECKED_IN', page: 0, size: PRE_CHECK_STAYS_SAMPLE_SIZE,
  });

  const pendingDeparturesCount = useMemo(
    () => (openStaysPage?.content ?? []).filter((s) => (s.expectedCheckOutDate ?? '') <= businessDate).length,
    [openStaysPage, businessDate],
  );

  if (pendingDeparturesCount === 0) return null;

  return (
    <Alert tone="warning" icon="info">
      <div className="flex items-center gap-2">
        <span>{t('night_audit_precheck_pending_departures', { count: pendingDeparturesCount })}</span>
        <Link to="/stays" state={OVERDUE_DEPARTURES_STATE} className="underline hover:no-underline font-medium">{t('view_all')}</Link>
      </div>
    </Alert>
  );
};
