import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Calendar, dateFnsLocalizer, type Event, type SlotInfo, type View } from 'react-big-calendar';
import { format, parse, startOfWeek, getDay, addMonths, subMonths, startOfMonth, startOfToday, isBefore, addDays, isValid } from 'date-fns';
import { enUS, it } from 'date-fns/locale';
import { reservationService } from '../services';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useToastStore } from '../store';
import type { ReservationResponse } from '../types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { PageHeader } from '../components/PageHeader';
import { M3StatusChip } from '../components/m3';
import { M3Card } from '../components/m3';
import { M3SegmentedRow } from '../components/m3';
import type { M3SegmentOption } from '../components/m3';
import PlanningBoard from '@/pages/PlanningBoard';
import { fetchAllRooms } from '../lib';
import type { RoomResponse } from '../types';
import { canMoveReservation, getStatusLabel } from './Reservations/reservationStatus';
import { getErrorMessage, resolveDesignToken, dateFnsLocale, reservationStatusTone, toneSolidTokens } from '../utils';

import 'react-big-calendar/lib/css/react-big-calendar.css';

const locales = { 'en-US': enUS, 'it-IT': it, en: enUS, it: it };
const localizer = dateFnsLocalizer({ format, parse, startOfWeek, getDay, locales });

interface ReservationEvent extends Event {
  resource: ReservationResponse;
}

const mapToEvent = (reservation: ReservationResponse, t: TFunction): ReservationEvent => ({
  title: `${reservation.guestFullName || t('guest_fallback', { id: reservation.guestId.slice(0, 8) })} (${getStatusLabel(reservation.status, t)})`,
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

type CalendarViewMode = 'month' | 'planning';
const VIEW_OPTIONS: M3SegmentOption<CalendarViewMode>[] = [
  { value: 'planning', labelKey: 'view_planning' },
  { value: 'month', labelKey: 'view_month' },
];
const CALENDAR_STYLE = { height: 600 };

const isStaleVersionError = (err: unknown): boolean =>
  (err as { response?: { data?: { errorCode?: string } } })?.response?.data?.errorCode === 'RESERVATION_STALE_VERSION';

const parseMonth = (value: string | null): Date | null => {
  if (!value || !/^\d{4}-\d{2}$/.test(value)) return null;
  const date = new Date(`${value}-01T00:00:00`);
  return isValid(date) ? date : null;
};

export const CalendarPlanning = () => {
  const { t, i18n } = useTranslation(['calendar', 'common']);
  const [reservations, setReservations] = useState<ReservationResponse[]>([]);
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // The visible month and view live in the URL (?month=2026-11&view=month) so that going back from
  // a reservation, a reload or a shared link reopens the same screen; anything unreadable falls
  // back to the current month / planning board.
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const view: CalendarViewMode = searchParams.get('view') === 'month' ? 'month' : 'planning';
  const monthParam = searchParams.get('month');
  const currentDate = useMemo(() => parseMonth(monthParam) ?? startOfMonth(new Date()), [monthParam]);

  const updateParams = useCallback((changes: { month?: Date; view?: CalendarViewMode }) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (changes.month) next.set('month', format(changes.month, 'yyyy-MM'));
      if (changes.view) next.set('view', changes.view);
      return next;
    }, { replace: true });
  }, [setSearchParams]);

  const setCurrentDate = useCallback((date: Date) => updateParams({ month: date }), [updateParams]);
  const setView = useCallback((next: CalendarViewMode) => updateParams({ view: next }), [updateParams]);
  const addToast = useToastStore((s) => s.addToast);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const [reservData, roomsData] = await Promise.all([
        reservationService.getAllReservations(),
        fetchAllRooms()
      ]);
      setReservations(reservData);
      setRooms(roomsData);
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
      // Guests already in house keep the room of their stay (changed from Stays) and closed
      // bookings are history: neither can be moved from here.
      if (!reservation || !canMoveReservation(reservation.status)) return;

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
        const saved = await reservationService.updateReservation(reservationId, {
          guestId: updatedReservation.guestId,
          checkInDate: updatedReservation.checkInDate,
          checkOutDate: updatedReservation.checkOutDate,
          status: updatedReservation.status,
          expectedGuests: updatedReservation.expectedGuests,
          // price is resolved server-side (RatePricingService) on every update —
          // moving a reservation to a different room now correctly re-prices it,
          // instead of carrying the old room's price along.
          lineItems: updatedReservation.lineItems.map(li => ({ roomId: li.roomId })),
          version: reservation.version,
        });
        // Keep the server's answer (new version, re-priced rooms) so a second move isn't stale.
        setReservations((prev) =>
          prev.map((r) => (r.id === reservationId ? { ...updatedReservation, ...saved } : r)),
        );
        addToast(t('room_moved_success', { name: updatedReservation.guestFullName || t('guest_fallback', { id: reservation.guestId.substring(0, 8) }) }), 'success');
      } catch (err: unknown) {
        setReservations((prev) =>
          prev.map((r) => (r.id === reservationId ? reservation : r)),
        );
        if (isStaleVersionError(err)) {
          addToast(t('room_move_stale'), 'error');
          loadData();
        } else {
          addToast(t('room_move_failed'), 'error');
        }
      }
    },
    [reservations, addToast, t, loadData],
  );

  const handleOpenReservation = useCallback((reservationId: string) => {
    navigate(`/reservations/${reservationId}`);
  }, [navigate]);

  // An empty day (and room, on the planning board) starts a new booking with those already set.
  const handleNewReservation = useCallback((date: Date, roomId?: string, checkOut?: Date) => {
    if (isBefore(date, startOfToday())) return;
    navigate('/reservations/new', {
      state: {
        checkInDate: format(date, 'yyyy-MM-dd'),
        checkOutDate: checkOut ? format(checkOut, 'yyyy-MM-dd') : undefined,
        roomId,
      },
    });
  }, [navigate]);

  // Dragging over several days books them all: the departure is the day after the last one picked.
  const handleSelectSlot = useCallback((slot: SlotInfo) => {
    const lastDay = slot.slots.length > 0 ? slot.slots[slot.slots.length - 1] : slot.start;
    handleNewReservation(slot.start, undefined, addDays(lastDay, 1));
  }, [handleNewReservation]);

  const handleSelectEvent = useCallback((event: object) => {
    handleOpenReservation((event as ReservationEvent).resource.id);
  }, [handleOpenReservation]);

  const handlePrevMonth = useCallback(() => {
    setCurrentDate(startOfMonth(subMonths(currentDate, 1)));
  }, [currentDate, setCurrentDate]);

  const handleNextMonth = useCallback(() => {
    setCurrentDate(startOfMonth(addMonths(currentDate, 1)));
  }, [currentDate, setCurrentDate]);

  const handleMonthChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.value) {
      const [year, month] = e.target.value.split('-').map(Number);
      setCurrentDate(new Date(year, month - 1, 1));
    }
  }, [setCurrentDate]);

  const events: ReservationEvent[] = useMemo(
    () => reservations.filter(r => r.active !== false).map(r => mapToEvent(r, t)),
    [reservations, t]
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
          <M3SegmentedRow
            options={VIEW_OPTIONS}
            value={view}
            onChange={setView}
            ariaLabel={t('calendar_view_label')}
            ns="common"
          />
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
          onReservationOpen={handleOpenReservation}
          onSlotSelect={handleNewReservation}
        />
      ) : (
        <M3Card variant="solid" className="p-4">
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
            onSelectEvent={handleSelectEvent}
            eventPropGetter={eventPropGetter as (event: object) => object}
            popup
          />
        </M3Card>
      )}
    </div>
  );
};
