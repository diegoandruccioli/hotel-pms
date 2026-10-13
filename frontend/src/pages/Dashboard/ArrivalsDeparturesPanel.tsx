import { useCallback, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { M3Card, M3SegmentedRow } from '../../components/m3';
import type { M3SegmentOption } from '../../components/m3';
import { M3EmptyState } from '../../components/m3';
import { M3TableActionLink } from '../../components/m3';
import { useToastStore } from '../../store';
import { getErrorMessage, todayIsoDate } from '../../utils';
import { useReservationsSearch, useStaysSearch, useCheckOutStay } from '../../hooks/queries';
import type { ReservationResponse, StayResponse } from '../../types';

const WIDGET_ROW_LIMIT = 8;

const CHECK_IN_ELIGIBLE_STATUS = 'CONFIRMED';

type PanelTab = 'arrivals' | 'departures';

const TAB_OPTIONS: M3SegmentOption<PanelTab>[] = [
  { value: 'arrivals', labelKey: 'dashboard_tab_arrivals', icon: 'login' },
  { value: 'departures', labelKey: 'dashboard_tab_departures', icon: 'logout' },
];

const VIEW_ALL_HREF: Record<PanelTab, string> = { arrivals: '/reservations', departures: '/stays' };

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
 * Front-desk "work list" widget: arrivals and due-outs for today in one card,
 * switched by a segmented row, with inline check-in/check-out actions per the
 * front-desk-dashboard convention (Cloudbeds "Today", Mews front-desk timeline):
 * actionable rows, not just counters. Complements the KPI cards above it.
 */
export const ArrivalsDeparturesPanel = () => {
  const { t } = useTranslation('common');
  const today = useMemo(() => todayIsoDate(), []);
  const [tab, setTab] = useState<PanelTab>('arrivals');

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
  const isArrivals = tab === 'arrivals';
  const isLoading = isArrivals ? arrivalsLoading : dueOutLoading;
  const isEmpty = isArrivals ? arrivals.length === 0 : dueOutStays.length === 0;

  return (
    <M3Card variant="solid" className="p-5">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h2 className="text-sm font-display font-semibold text-on-surface">{t('dashboard_arrivals_departures_title')}</h2>
        <div className="flex items-center gap-3">
          <M3SegmentedRow<PanelTab>
            options={TAB_OPTIONS}
            value={tab}
            onChange={setTab}
            ariaLabel={t('dashboard_arrivals_departures_switch')}
            ns="common"
            className="w-56"
          />
          <Link
            to={VIEW_ALL_HREF[tab]}
            aria-label={t(isArrivals ? 'dashboard_view_all_arrivals' : 'dashboard_view_all_departures')}
            className="inline-flex items-center min-h-10 text-sm font-medium font-body text-primary hover:text-primary/80 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-sm whitespace-nowrap"
          >
            {t('view_all')}
          </Link>
        </div>
      </div>
      {isLoading ? (
        <div className="py-8 text-center text-sm font-body text-on-surface-variant">…</div>
      ) : isEmpty ? (
        <M3EmptyState
          icon={isArrivals ? 'event_available' : 'event_busy'}
          title={t(isArrivals ? 'dashboard_no_arrivals_today' : 'dashboard_no_departures_today')}
        />
      ) : (
        <ul className="divide-y divide-outline-variant">
          {isArrivals
            ? arrivals.map((r) => <ArrivalRow key={r.id} reservation={r} />)
            : dueOutStays.map((st) => <DepartureRow key={st.id} stay={st} />)}
        </ul>
      )}
    </M3Card>
  );
};
