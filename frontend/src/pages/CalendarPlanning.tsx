import { useState, useEffect, useCallback, useMemo } from 'react';
import { Calendar, dateFnsLocalizer, type Event, type View } from 'react-big-calendar';
import { format, parse, startOfWeek, getDay, addMonths, subMonths, startOfMonth } from 'date-fns';
import { enUS, it } from 'date-fns/locale';
import { reservationService } from '../services/reservationService';
import { useTranslation } from 'react-i18next';
import { useToastStore } from '../store/toastStore';
import type { ReservationResponse } from '../types/reservation.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3StatusChip } from '../components/m3/M3StatusChip';
import { M3Card } from '../components/m3/M3Card';
import PlanningBoard from '@/pages/PlanningBoard';
import { inventoryService } from '../services/inventoryService';
import type { RoomResponse } from '../types/inventory.types';

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



type StatusTone = 'info' | 'warning' | 'success' | 'neutral' | 'error';

const statusLegend: { labelKey: string; color: string; tone: StatusTone }[] = [
  { labelKey: 'status_confirmed', color: '#1A3A5C', tone: 'info' },
  { labelKey: 'status_pending', color: '#B8860B', tone: 'warning' },
  { labelKey: 'status_checked_in', color: '#2E7D6A', tone: 'success' },
  { labelKey: 'status_checked_out', color: '#73777F', tone: 'neutral' },
  { labelKey: 'status_cancelled', color: '#BA1A1A', tone: 'error' },
];

const CALENDAR_VIEWS: View[] = ['month'];
const CALENDAR_STYLE = { height: 600 };

export const CalendarPlanning = () => {
  const { t, i18n } = useTranslation('common');
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
      const message = err instanceof Error ? err.message : t('failed_load_data');
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
          lineItems: updatedReservation.lineItems.map(li => ({
            roomId: li.roomId,
            price: li.price
          }))
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
    const colorMap: Record<string, string> = {
      CONFIRMED: '#1A3A5C',
      PENDING: '#B8860B',
      CANCELLED: '#BA1A1A',
      CHECKED_IN: '#2E7D6A',
      CHECKED_OUT: '#73777F',
    };
    const bg = colorMap[status] ?? '#1A3A5C';
    return { style: { backgroundColor: bg, borderColor: bg, color: '#fff', borderRadius: '8px' } };
  }, []);

  const currentYear = format(currentDate, 'yyyy');
  const monthName = format(currentDate, 'MMMM', { locale: i18n.language.startsWith('it') ? it : enUS });
  const monthValue = format(currentDate, 'yyyy-MM');

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="grid grid-cols-1 lg:grid-cols-3 items-center gap-6">
        {/* Title Group */}
        <div className="flex flex-col">
          <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center gap-2">
            <MaterialIcon name="date_range" className="text-primary" />
            {t('nav_calendar')}
          </h1>
          <p className="text-sm font-body text-on-surface-variant mt-1">{t('calendar_subtitle')}</p>
        </div>

        {/* Central Navigator */}
        <div className="flex items-center justify-center gap-2 bg-surface-container-low px-4 py-2 rounded-shape-full shadow-elevation-1">
          <button
            onClick={handlePrevMonth}
            className="p-1 rounded-full hover:bg-surface-container transition-colors text-primary"
            aria-label="Previous month"
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
            className="p-1 rounded-full hover:bg-surface-container transition-colors text-primary"
            aria-label="Next month"
          >
            <MaterialIcon name="chevron_right" size={28} />
          </button>
          
          {/* Hidden input for the native month picker */}
          <div className="relative ml-2">
            <input
              type="month"
              value={monthValue}
              onChange={handleMonthChange}
              className="absolute inset-0 opacity-0 cursor-pointer w-8 h-8"
              title={t('select_month')}
            />
            <MaterialIcon name="calendar_month" className="text-on-surface-variant opacity-60" />
          </div>
        </div>

        {/* View Switcher Group */}
        <div className="flex justify-start lg:justify-end">
          <div className="flex bg-surface-container rounded-shape-full p-1">
            <button
              onClick={setPlanningView}
              className={`px-4 py-1.5 text-sm font-medium rounded-shape-full transition-all ${
                view === 'planning' ? 'bg-primary text-on-primary shadow-elevation-1' : 'text-on-surface-variant hover:bg-surface-container-high'
              }`}
            >
              {t('view_planning')}
            </button>
            <button
              onClick={setMonthView}
              className={`px-4 py-1.5 text-sm font-medium rounded-shape-full transition-all ${
                view === 'month' ? 'bg-primary text-on-primary shadow-elevation-1' : 'text-on-surface-variant hover:bg-surface-container-high'
              }`}
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
        <div className="flex justify-center items-center h-96 bg-surface rounded-shape-md shadow-elevation-1">
          <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
        </div>
      ) : error ? (
        <div className="flex items-center gap-3 px-4 py-4 rounded-shape-sm bg-error-container text-on-error-container">
          <MaterialIcon name="error" size={20} className="flex-shrink-0" />
          <div>
            <h3 className="text-sm font-medium font-body">{t('error_loading_reservations')}</h3>
            <p className="mt-1 text-sm font-body opacity-80">{error}</p>
            <button type="button" onClick={loadData} className="mt-2 text-sm font-medium underline hover:no-underline">
              {t('try_again')}
            </button>
          </div>
        </div>
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
