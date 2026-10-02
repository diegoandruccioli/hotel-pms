import { useState, useEffect, useCallback, useMemo } from 'react';
import { Calendar, dateFnsLocalizer, type Event, type View } from 'react-big-calendar';
import { format, parse, startOfWeek, getDay, addMonths, subMonths, startOfMonth } from 'date-fns';
import { enUS, it } from 'date-fns/locale';
import { reservationService } from '../services';
import { useTranslation } from 'react-i18next';
import { useToastStore } from '../store';
import type { ReservationResponse } from '../types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { PageHeader } from '../components/PageHeader';
import { M3StatusChip } from '../components/m3';
import { M3Card } from '../components/m3';
import PlanningBoard from '@/pages/PlanningBoard';
import { inventoryService } from '../services';
import type { RoomResponse } from '../types';
import { getErrorMessage, cn, resolveDesignToken, dateFnsLocale, reservationStatusTone, toneSolidTokens } from '../utils';

import 'react-big-calendar/lib/css/react-big-calendar.css';

const locales = { 'en-US': enUS, 'it-IT': it, en: enUS, it: it };
const localizer = dateFnsLocalizer({ format, parse, startOfWeek, getDay, locales });



interface ReservationEvent extends Event {
  resource: ReservationResponse;
}



const mapToEvent = (reservation: ReservationResponse): ReservationEvent => ({
  title: `${reservation.guestFullName || `Guest ${reservation.guestId.slice(0, 8)}`} (${reservation.status})`,
  start: new Date(reservation.checkInDate),
  end: new Date(reservation.checkOutDate),
  resource: reservation,
});



// `tone` alone drives the rendered swatch (M3StatusChip resolves it to the
// matching M3 token) -- derived from the shared reservation map so the legend
// can't drift from the list; the solid event blocks below use toneSolidTokens,
// since react-big-calendar needs actual resolved color values, not a tone.
const LEGEND_STATUSES = ['CONFIRMED', 'PENDING', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED'] as const;
const statusLegend = LEGEND_STATUSES.map((status) => ({
  labelKey: `status_${status.toLowerCase()}`,
  tone: reservationStatusTone[status],
}));

const CALENDAR_VIEWS: View[] = ['month'];
const CALENDAR_STYLE = { height: 600 };

export const CalendarPlanning = () => {
  const { t, i18n } = useTranslation(['calendar', 'common']);
  const [reservations, setReservations] = useState<ReservationResponse[]>([]);
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [view, setView] = useState<'month' | 'planning'>('planning');
  const [error, setError] = useState<string | null>(null);
  const [currentDate, setCurrentDate] = useState(new Date());
  const addToast = useToastStore((s) => s.addToast);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const [reservData, roomsData] = await Promise.all([
        reservationService.getAllReservations(),
        inventoryService.getAllRooms(0, 500)
      ]);
      setReservations(reservData);
      setRooms(roomsData.content);
    } catch (err: unknown) {
      const message = getErrorMessage(err, t('failed_load_data'));
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    loadData();
  }, [loadData]);


  const handlePlanningBoardDrop = useCallback(
    async (reservationId: string, oldRoomId: string, newRoomId: string) => {
      const reservation = reservations.find(r => r.id === reservationId);
      if (!reservation) return;

      const hasOverlap = reservations.some(r => {
        if (r.id === reservationId || r.active === false || r.status === 'CANCELLED') return false;
        
        const inTargetRoom = r.lineItems.some(li => li.active !== false && li.roomId === newRoomId);
        if (!inTargetRoom) return false;

        const nIn = new Date(reservation.checkInDate).getTime();
        const nOut = new Date(reservation.checkOutDate).getTime();
        const rIn = new Date(r.checkInDate).getTime();
        const rOut = new Date(r.checkOutDate).getTime();

        return nIn < rOut && nOut > rIn;
      });

      if (hasOverlap) {
        addToast(t('room_move_overlap_error'), 'error');
        return;
      }

      const updatedReservation = {
        ...reservation,
        lineItems: reservation.lineItems
          .filter(li => li.active !== false)
          .map(li => {
            if (li.roomId === oldRoomId) {
              return { ...li, roomId: newRoomId };
            }
            return li;
          })
      };

      setReservations((prev) =>
        prev.map((r) => (r.id === reservationId ? updatedReservation : r)),
      );

      try {
        await reservationService.updateReservation(reservationId, {
          guestId: updatedReservation.guestId,
          checkInDate: updatedReservation.checkInDate,
          checkOutDate: updatedReservation.checkOutDate,
          status: updatedReservation.status,
          expectedGuests: updatedReservation.expectedGuests,
          // price is resolved server-side (RatePricingService) on every update —
          // moving a reservation to a different room now correctly re-prices it,
          // instead of carrying the old room's price along.
          lineItems: updatedReservation.lineItems.map(li => ({ roomId: li.roomId }))
        });
        addToast(t('room_moved_success', { name: updatedReservation.guestFullName || `Guest ${reservation.guestId.substring(0, 8)}` }), 'success');
      } catch {
        setReservations((prev) =>
          prev.map((r) => (r.id === reservationId ? reservation : r)),
        );
        addToast(t('room_move_failed'), 'error');
      }
    },
    [reservations, addToast, t],
  );

  const handleSelectSlot = useCallback(() => {
    // Future: open a "New Reservation" modal
  }, []);

  const handlePrevMonth = useCallback(() => {
    setCurrentDate(prev => startOfMonth(subMonths(prev, 1)));
  }, []);

  const handleNextMonth = useCallback(() => {
    setCurrentDate(prev => startOfMonth(addMonths(prev, 1)));
  }, []);

  const handleMonthChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.value) {
      const [year, month] = e.target.value.split('-').map(Number);
      setCurrentDate(new Date(year, month - 1, 1));
    }
  }, []);

  const setPlanningView = useCallback(() => setView('planning'), []);
  const setMonthView = useCallback(() => setView('month'), []);

  const events: ReservationEvent[] = useMemo(
    () => reservations.filter(r => r.active !== false).map(mapToEvent),
    [reservations]
  );

  const eventPropGetter = useCallback((event: ReservationEvent) => {
    const status = event.resource.status;
    // A status the map doesn't know (backend ahead of the frontend) renders neutral.
    const tokens = toneSolidTokens[reservationStatusTone[status] ?? 'neutral'];
    // Resolved live (not memoized) so a theme change (light/dark, high-contrast)
    // is reflected on the next render instead of freezing whatever was current
    // when this callback happened to be created.
    const bg = resolveDesignToken(tokens.bg);
    const text = resolveDesignToken(tokens.text);
    const border = resolveDesignToken(tokens.border);
    return { style: { backgroundColor: bg, borderColor: border, color: text, borderRadius: '8px' } };
  }, []);

  const currentYear = format(currentDate, 'yyyy');
  const monthName = format(currentDate, 'MMMM', { locale: dateFnsLocale(i18n.language) });
  const monthValue = format(currentDate, 'yyyy-MM');

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="grid grid-cols-1 lg:grid-cols-3 items-center gap-6">
        {/* Title Group */}
        <PageHeader icon="date_range" title={t('nav_calendar')} subtitle={t('calendar_subtitle')} />

        {/* Central Navigator */}
        <div className="flex items-center justify-center gap-2 bg-surface-container-low px-4 py-2 rounded-shape-full shadow-elevation-1">
          <button
            onClick={handlePrevMonth}
            className="flex items-center justify-center min-h-10 min-w-10 rounded-full hover:bg-surface-container transition-colors text-primary"
            aria-label={t('prev_month')}
          >
            <MaterialIcon name="chevron_left" size={28} />
          </button>
          
          <div className="flex flex-col items-center min-w-[140px]">
            <span className="text-xs font-bold uppercase tracking-widest text-primary opacity-70">
              {currentYear}
            </span>
            <span className="text-lg font-display font-bold text-on-surface capitalize leading-tight">
              {monthName}
            </span>
          </div>

          <button
            onClick={handleNextMonth}
            className="flex items-center justify-center min-h-10 min-w-10 rounded-full hover:bg-surface-container transition-colors text-primary"
            aria-label={t('next_month')}
          >
            <MaterialIcon name="chevron_right" size={28} />
          </button>

          {/* Hidden input for the native month picker */}
          <div className="relative ml-2 flex items-center justify-center min-h-10 min-w-10">
            <input
              type="month"
              value={monthValue}
              onChange={handleMonthChange}
              className="absolute inset-0 opacity-0 cursor-pointer"
              title={t('select_month')}
              aria-label={t('select_month')}
            />
            <MaterialIcon name="calendar_month" className="text-on-surface-variant opacity-60" />
          </div>
        </div>

        {/* View Switcher Group */}
        <div className="flex justify-start lg:justify-end">
          <div className="flex bg-surface-container rounded-shape-full p-1">
            <button
              onClick={setPlanningView}
              className={cn(
                'px-4 py-1.5 text-sm font-medium rounded-shape-full transition-all',
                view === 'planning' ? 'bg-primary text-on-primary shadow-elevation-1' : 'text-on-surface-variant hover:bg-surface-container-high'
              )}
            >
              {t('view_planning')}
            </button>
            <button
              onClick={setMonthView}
              className={cn(
                'px-4 py-1.5 text-sm font-medium rounded-shape-full transition-all',
                view === 'month' ? 'bg-primary text-on-primary shadow-elevation-1' : 'text-on-surface-variant hover:bg-surface-container-high'
              )}
            >
              {t('view_month')}
            </button>
          </div>
        </div>
      </div>

      {/* Legend */}
      <div className="flex flex-wrap gap-3">
        {statusLegend.map(({ labelKey, tone }) => (
          <M3StatusChip key={labelKey} label={t(labelKey)} tone={tone} />
        ))}
      </div>

      {/* Calendar body */}
      {loading ? (
        <M3LoadingState label={t('common:loading')} className="h-96" />
      ) : error ? (
        <M3ErrorState
          title={t('error_loading_reservations')}
          message={error}
          retryLabel={t('try_again')}
          onRetry={loadData}
        />
      ) : view === 'planning' ? (
        <PlanningBoard
          rooms={rooms}
          reservations={reservations}
          currentDate={currentDate}
          onNavigate={setCurrentDate}
          onReservationMove={handlePlanningBoardDrop}
        />
      ) : (
        <M3Card variant="outlined" className="p-4">
          <Calendar
            localizer={localizer}
            culture={i18n.language}
            events={events}
            defaultView="month"
            views={CALENDAR_VIEWS}
            toolbar={false}
            date={currentDate}
            onNavigate={setCurrentDate}
            style={CALENDAR_STYLE}
            selectable
            onSelectSlot={handleSelectSlot}
            eventPropGetter={eventPropGetter as (event: object) => object}
            popup
          />
        </M3Card>
      )}
    </div>
  );
};
