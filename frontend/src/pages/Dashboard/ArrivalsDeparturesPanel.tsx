import { useCallback, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Card } from '../../components/m3';
import { M3EmptyState } from '../../components/m3';
import { M3TableActionLink } from '../../components/m3';
import { useToastStore } from '../../store';
import { getErrorMessage } from '../../utils';
import { useReservationsSearch, useStaysSearch, useCheckOutStay } from '../../hooks/queries';
import type { ReservationResponse, StayResponse } from '../../types';

const WIDGET_ROW_LIMIT = 8;

const todayIsoDate = (): string => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

const CHECK_IN_ELIGIBLE_STATUS = 'CONFIRMED';

interface PanelShellProps {
  icon: string;
  title: string;
  viewAllHref: string;
  viewAllLabel: string;
  isLoading: boolean;
  isEmpty: boolean;
  emptyIcon: string;
  emptyTitle: string;
  children: React.ReactNode;
}

const PanelShell = ({
  icon, title, viewAllHref, viewAllLabel, isLoading, isEmpty, emptyIcon, emptyTitle, children,
}: PanelShellProps) => (
  <M3Card variant="outlined" className="p-5">
    <div className="flex items-center justify-between mb-4">
      <div className="flex items-center gap-2">
        <MaterialIcon name={icon} size={20} className="text-primary" />
        <h2 className="text-sm font-display font-semibold text-on-surface">{title}</h2>
      </div>
      <Link
        to={viewAllHref}
        className="inline-flex items-center min-h-10 text-sm font-medium font-body text-primary hover:text-primary/80 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-sm"
      >
        {viewAllLabel}
      </Link>
    </div>
    {isLoading ? (
      <div className="py-8 text-center text-sm font-body text-on-surface-variant">…</div>
    ) : isEmpty ? (
      <M3EmptyState icon={emptyIcon} title={emptyTitle} />
    ) : (
      <ul className="divide-y divide-outline-variant">{children}</ul>
    )}
  </M3Card>
);

const ArrivalRow = ({ reservation }: { reservation: ReservationResponse }) => {
  const { t } = useTranslation('common');
  const navigate = useNavigate();

  const handleCheckIn = useCallback(() => {
    navigate(`/stays/check-in/${reservation.id}`, {
      state: {
        roomId: reservation.lineItems?.[0]?.roomId || '',
        expectedGuests: reservation.expectedGuests,
        guestId: reservation.guestId,
      },
    });
  }, [navigate, reservation]);

  return (
    <li className="flex items-center justify-between gap-3 py-2.5">
      <div className="min-w-0">
        <p className="text-sm font-body font-medium text-on-surface truncate">{reservation.guestFullName}</p>
        <p className="text-xs font-body text-on-surface-variant">
          {t('guests')}: {reservation.expectedGuests}
        </p>
      </div>
      {reservation.status === CHECK_IN_ELIGIBLE_STATUS && (
        <M3TableActionLink onClick={handleCheckIn} data-testid={`dashboard-check-in-${reservation.id}`}>
          {t('check_in')}
        </M3TableActionLink>
      )}
    </li>
  );
};

const DepartureRow = ({ stay }: { stay: StayResponse }) => {
  const { t } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);
  const checkOutMutation = useCheckOutStay();

  const handleCheckOut = useCallback(async () => {
    try {
      await checkOutMutation.mutateAsync(stay.id);
      addToast(t('guest_checked_out_success'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('checkout_failed')), 'error');
    }
  }, [checkOutMutation, stay.id, addToast, t]);

  return (
    <li className="flex items-center justify-between gap-3 py-2.5">
      <div className="min-w-0">
        <p className="text-sm font-body font-medium text-on-surface truncate">
          {stay.roomNumber ?? '—'}
        </p>
        <p className="text-xs font-body text-on-surface-variant">
          {t('night_audit_departures')}: {stay.expectedCheckOutDate ?? '—'}
        </p>
      </div>
      <M3TableActionLink
        onClick={handleCheckOut}
        disabled={checkOutMutation.isPending}
        data-testid={`dashboard-check-out-${stay.id}`}
      >
        {t('check_out')}
      </M3TableActionLink>
    </li>
  );
};

/**
 * Front-desk "work list" widget — arrivals and due-outs for today, with
 * inline check-in/check-out actions, per the front-desk-dashboard convention
 * (Cloudbeds "Today", Mews front-desk timeline): actionable rows, not just
 * counters. Complements, doesn't replace, the KPI cards above it.
 */
export const ArrivalsDeparturesPanel = () => {
  const { t } = useTranslation('common');
  const today = useMemo(() => todayIsoDate(), []);

  const { data: arrivalsPage, isLoading: arrivalsLoading } = useReservationsSearch({
    query: '',
    upcomingOnly: false,
    dateFrom: today,
    dateTo: today,
    page: 0,
    size: WIDGET_ROW_LIMIT,
    sort: 'checkInDate,asc',
  });

  const { data: dueOutPage, isLoading: dueOutLoading } = useStaysSearch({
    status: 'CHECKED_IN',
    page: 0,
    size: 50,
  });

  const dueOutStays = useMemo(() => {
    const stays = dueOutPage?.content ?? [];
    return [...stays]
      .filter((s) => (s.expectedCheckOutDate ?? '') <= today)
      .sort((a, b) => (a.expectedCheckOutDate ?? '').localeCompare(b.expectedCheckOutDate ?? ''))
      .slice(0, WIDGET_ROW_LIMIT);
  }, [dueOutPage, today]);

  const arrivals = arrivalsPage?.content ?? [];

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
      <PanelShell
        icon="login"
        title={t('dashboard_arrivals_today')}
        viewAllHref="/reservations"
        viewAllLabel={t('view_all')}
        isLoading={arrivalsLoading}
        isEmpty={arrivals.length === 0}
        emptyIcon="event_available"
        emptyTitle={t('dashboard_no_arrivals_today')}
      >
        {arrivals.map((r) => <ArrivalRow key={r.id} reservation={r} />)}
      </PanelShell>

      <PanelShell
        icon="logout"
        title={t('dashboard_departures_today')}
        viewAllHref="/stays"
        viewAllLabel={t('view_all')}
        isLoading={dueOutLoading}
        isEmpty={dueOutStays.length === 0}
        emptyIcon="event_busy"
        emptyTitle={t('dashboard_no_departures_today')}
      >
        {dueOutStays.map((s) => <DepartureRow key={s.id} stay={s} />)}
      </PanelShell>
    </div>
  );
};
