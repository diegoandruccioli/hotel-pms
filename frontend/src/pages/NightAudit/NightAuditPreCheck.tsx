import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Alert } from '../../components/Alert';
import { useReservationsSearch, useStaysSearch } from '../../hooks/queries';

const PRE_CHECK_STAYS_SAMPLE_SIZE = 100;
const NOT_CHECKED_IN_STATUS = 'CONFIRMED';

/**
 * Pre-close checklist for the selected business date, per the OPERA End of
 * Day sequence convention (arrivals not checked in / departures not checked
 * out are surfaced before the close, not discovered after). Informational
 * only — the run button stays enabled either way, same as OPERA's "run
 * anyway" pattern; this just tells the auditor what will be swept up as a
 * no-show / left open.
 */
export const NightAuditPreCheck = ({ businessDate }: { businessDate: string }) => {
  const { t } = useTranslation('common');

  const { data: pendingArrivals } = useReservationsSearch({
    query: '', upcomingOnly: false, page: 0, size: 1,
    dateTo: businessDate, status: NOT_CHECKED_IN_STATUS, sort: 'checkInDate,asc',
  });
  const { data: openStaysPage } = useStaysSearch({
    status: 'CHECKED_IN', page: 0, size: PRE_CHECK_STAYS_SAMPLE_SIZE,
  });

  const pendingArrivalsCount = pendingArrivals?.totalElements ?? 0;
  const pendingDeparturesCount = useMemo(
    () => (openStaysPage?.content ?? []).filter((s) => (s.expectedCheckOutDate ?? '') <= businessDate).length,
    [openStaysPage, businessDate],
  );

  if (pendingArrivalsCount === 0 && pendingDeparturesCount === 0) return null;

  return (
    <Alert tone="warning" icon="info">
      <div className="flex flex-col gap-2">
        {pendingArrivalsCount > 0 && (
          <div className="flex items-center gap-2">
            <span>{t('night_audit_precheck_pending_arrivals', { count: pendingArrivalsCount })}</span>
            <Link to="/reservations" className="underline hover:no-underline font-medium">{t('view_all')}</Link>
          </div>
        )}
        {pendingDeparturesCount > 0 && (
          <div className="flex items-center gap-2">
            <span>{t('night_audit_precheck_pending_departures', { count: pendingDeparturesCount })}</span>
            <Link to="/stays" className="underline hover:no-underline font-medium">{t('view_all')}</Link>
          </div>
        )}
      </div>
    </Alert>
  );
};
