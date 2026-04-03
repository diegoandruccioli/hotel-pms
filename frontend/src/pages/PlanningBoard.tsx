import React, { useState, useMemo, useRef, useEffect, memo, useCallback } from 'react';
import { format, eachDayOfInterval, startOfMonth, endOfMonth, isSameDay, differenceInCalendarDays, addMonths } from 'date-fns';
import { it, enUS } from 'date-fns/locale';
import { useTranslation } from 'react-i18next';
import { M3Card } from '../components/m3/M3Card';
import type { RoomResponse } from '../types/inventory.types';
import type { ReservationResponse } from '../types/reservation.types';

interface PlanningBoardProps {
  rooms: RoomResponse[];
  reservations: ReservationResponse[];
  currentDate: Date;
  onNavigate: (date: Date) => void;
  onReservationMove?: (reservationId: string, oldRoomId: string, newRoomId: string) => void;
}

const CELL_WIDTH = 100;
const SIDEBAR_WIDTH = 192;
const ROW_HEIGHT = 64;

const STATUS_COLORS: Record<string, string> = {
  CONFIRMED: 'bg-primary text-on-primary',
  PENDING: 'bg-tertiary text-on-tertiary',
  CHECKED_IN: 'bg-secondary text-on-secondary',
  CHECKED_OUT: 'bg-outline text-on-surface-variant',
  CANCELLED: 'bg-error text-on-error',
};

const ROW_STYLE = { height: ROW_HEIGHT };
const SIDEBAR_STYLE = { width: SIDEBAR_WIDTH };
const CELL_STYLE = { width: CELL_WIDTH };

const ReservationBar = memo(({ 
  reservation, 
  monthStart, 
  monthEnd, 
  nextMonthStart, 
  CELL_WIDTH,
  roomId,
}: {
  reservation: ReservationResponse;
  monthStart: Date;
  monthEnd: Date;
  nextMonthStart: Date;
  CELL_WIDTH: number;
  roomId: string;
}) => {
  const [isDragging, setIsDragging] = useState(false);
  const startRaw = new Date(reservation.checkInDate);
  const endRaw = new Date(reservation.checkOutDate);
  
  const visibleStart = startRaw < monthStart ? monthStart : startRaw;
  const visibleEnd = endRaw > monthEnd ? nextMonthStart : endRaw;
  
  // Posizionamento basato su metà giornata (mezzogiorno)
  // Se la prenotazione inizia prima della vista: inizia a 0.0 (copre tutta la mattina)
  // Se la prenotazione inizia nella vista: inizia a 0.5 (mezzogiorno)
  // Se la prenotazione finisce dopo la vista: finisce a 1.0 (copre tutto il pomeriggio)
  // Se la prenotazione finisce nella vista: finisce a 0.5 (mezzogiorno)
  const startAdjust = startRaw < monthStart ? 0 : 0.5;
  const endAdjust = endRaw > monthEnd ? 0 : 0.5;

  const startPx = (differenceInCalendarDays(visibleStart, monthStart) + startAdjust) * CELL_WIDTH;
  const endPx = (differenceInCalendarDays(visibleEnd, monthStart) + endAdjust) * CELL_WIDTH;
  
  const leftOffset = startPx;
  const width = Math.max(endPx - startPx, CELL_WIDTH / 2);

  const style = useMemo(() => ({ 
    left: leftOffset, 
    width: width,
    opacity: isDragging ? 0.4 : 1,
  }), [leftOffset, width, isDragging]);

  const handleDragStart = useCallback((e: React.DragEvent) => {
    e.dataTransfer.setData('application/json', JSON.stringify({
      reservationId: reservation.id,
      oldRoomId: roomId
    }));
    e.dataTransfer.effectAllowed = 'move';
    setIsDragging(true);
  }, [reservation.id, roomId]);

  const handleDragEnd = useCallback(() => {
    setIsDragging(false);
  }, []);

  if (startRaw > monthEnd || endRaw < monthStart) return null;

  return (
    <div
      role="button"
      tabIndex={0}
      draggable
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
      className={`absolute top-2 h-12 rounded-shape-sm shadow-elevation-1 p-2 flex flex-col justify-center cursor-grab hover:shadow-elevation-2 transition-all z-0 overflow-hidden ${STATUS_COLORS[reservation.status] || STATUS_COLORS.CONFIRMED}`}
      style={style}
      title={`${reservation.guestFullName} (${reservation.status})`}
    >
      <span className="text-xs font-bold truncate leading-tight">
        {reservation.guestFullName || 'Guest'}
      </span>
      <span className="text-[10px] opacity-80 truncate">
        {format(startRaw, 'dd/MM')} - {format(endRaw, 'dd/MM')}
      </span>
    </div>
  );
});

ReservationBar.displayName = 'ReservationBar';

interface RoomRowProps {
  room: RoomResponse;
  allocations: { reservation: ReservationResponse; roomId: string }[];
  onDragOver: (e: React.DragEvent) => void;
  onDrop: (e: React.DragEvent, roomId: string) => void;
  monthStart: Date;
  monthEnd: Date;
  nextMonthStart: Date;
  CELL_WIDTH: number;
}

const RoomRow = memo(({ room, allocations, onDragOver, onDrop, monthStart, monthEnd, nextMonthStart, CELL_WIDTH }: RoomRowProps) => {
  const [isDragOver, setIsDragOver] = useState(false);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    onDragOver(e);
    setIsDragOver(true);
  }, [onDragOver]);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    const relatedTarget = e.relatedTarget as Node | null;
    if (e.currentTarget.contains(relatedTarget)) return;
    setIsDragOver(false);
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      setIsDragOver(false);
      onDrop(e, room.id);
    },
    [onDrop, room.id]
  );

  return (
    // eslint-disable-next-line jsx-a11y/no-static-element-interactions
    <div
      style={ROW_STYLE}
      className={`border-b border-outline-variant relative transition-colors ${
        isDragOver ? 'bg-primary-container/30 border-l-4 border-l-primary' : ''
      }`}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      {allocations.map(({ reservation }) => (
        <ReservationBar
          key={`${reservation.id}-${room.id}`}
          reservation={reservation}
          monthStart={monthStart}
          monthEnd={monthEnd}
          nextMonthStart={nextMonthStart}
          CELL_WIDTH={CELL_WIDTH}
          roomId={room.id}
        />
      ))}
    </div>
  );
});

RoomRow.displayName = 'RoomRow';

const PlanningBoard: React.FC<PlanningBoardProps> = memo(({
  rooms,
  reservations,
  currentDate,
  onReservationMove,
}) => {
  const { t, i18n } = useTranslation('common');
  const locale = i18n.language.startsWith('it') ? it : enUS;
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  const { monthStart, monthEnd, nextMonthStart, days } = useMemo(() => {
    const start = startOfMonth(currentDate);
    const end = endOfMonth(currentDate);
    return {
      monthStart: start,
      monthEnd: end,
      nextMonthStart: addMonths(start, 1),
      days: eachDayOfInterval({ start, end })
    };
  }, [currentDate]);
  
  useEffect(() => {
    if (scrollContainerRef.current) {
      scrollContainerRef.current.scrollLeft = 0;
    }
  }, [monthStart]);

  const EMPTY_ALLOCATIONS: { reservation: ReservationResponse; roomId: string }[] = useMemo(() => [], []);

  const roomAllocations = useMemo(() => {
    const allocations: Record<string, { reservation: ReservationResponse; roomId: string }[]> = {};
    
    reservations.forEach(res => {
      if (res.active === false) return;
      res.lineItems.forEach(item => {
        if (item.active === false) return;
        if (!allocations[item.roomId]) allocations[item.roomId] = [];
        allocations[item.roomId].push({ reservation: res, roomId: item.roomId });
      });
    });
    
    return allocations;
  }, [reservations]);

  const totalWidth = useMemo(() => SIDEBAR_WIDTH + (days.length * CELL_WIDTH), [days.length]);
  const timelineWidth = useMemo(() => days.length * CELL_WIDTH, [days.length]);

  const containerStyle = useMemo(() => ({ width: totalWidth, minHeight: '100%' }), [totalWidth]);
  const timelineStyle = useMemo(() => ({ width: timelineWidth }), [timelineWidth]);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
  }, []);

  const handleDrop = useCallback((e: React.DragEvent, targetRoomId: string) => {
    e.preventDefault();
    try {
      const payload = e.dataTransfer.getData('application/json');
      if (!payload) return;
      const data = JSON.parse(payload);
      if (data.reservationId && data.oldRoomId && data.oldRoomId !== targetRoomId) {
        onReservationMove?.(data.reservationId, data.oldRoomId, targetRoomId);
      }
    } catch {
      // Ignore parsing errors for unexpected drag events
    }
  }, [onReservationMove]);

  return (
    <M3Card variant="outlined" className="overflow-hidden flex flex-col flex-1 min-h-0 border-outline-variant">
      <div 
        className="flex-1 overflow-auto relative" 
        ref={scrollContainerRef}
      >
        <div style={containerStyle}>
          
          <div 
            className="sticky top-0 z-40 flex bg-surface-container-low border-b border-outline-variant"
            style={ROW_STYLE}
          >
            <div 
              className="sticky left-0 z-50 h-full bg-surface-container-low border-r border-outline-variant flex flex-col justify-center px-4 font-display font-bold text-sm text-primary shadow-elevation-1"
              style={SIDEBAR_STYLE}
            >
              {t('nav_rooms')}
            </div>
            
            <div className="flex flex-1">
              {days.map(day => (
                <div 
                  key={day.toISOString()} 
                  style={CELL_STYLE}
                  className={`flex-shrink-0 border-r border-outline-variant flex flex-col items-center justify-center
                    ${isSameDay(day, new Date()) ? 'bg-primary-container text-on-primary-container' : ''}
                  `}
                >
                  <span className="text-[10px] uppercase font-bold tracking-wider opacity-60">
                    {format(day, 'EEE', { locale })}
                  </span>
                  <span className="text-lg font-display font-medium leading-none">
                    {format(day, 'd')}
                  </span>
                </div>
              ))}
            </div>
          </div>

          <div className="flex relative items-start">
            
            <div 
              className="sticky left-0 z-30 flex-shrink-0 bg-surface-container-low border-r border-outline-variant shadow-elevation-1"
              style={SIDEBAR_STYLE}
            >
              {rooms.map(room => (
                <div 
                  key={room.id} 
                  style={ROW_STYLE}
                  className="border-b border-outline-variant flex flex-col justify-center px-4 hover:bg-surface-container transition-colors bg-surface-container-low"
                >
                  <div className="flex items-center gap-2">
                    <span className="font-display font-bold text-on-surface">{room.roomNumber}</span>
                    <div className={`w-2 h-2 rounded-full ${room.status === 'CLEAN' ? 'bg-success' : room.status === 'DIRTY' ? 'bg-error' : 'bg-warning'}`} />
                  </div>
                  <span className="text-xs text-on-surface-variant line-clamp-1">{room.roomType.name}</span>
                </div>
              ))}
              {rooms.length === 0 && (
                <div className="p-4 text-sm text-on-surface-variant opacity-60 italic">
                  {t('no_rooms_found')}
                </div>
              )}
            </div>

            <div className="flex-1 relative" style={timelineStyle}>
              <div className="absolute inset-0 flex pointer-events-none z-0">
                {days.map(day => (
                  <div 
                    key={`grid-${day.toISOString()}`} 
                    style={CELL_STYLE}
                    className="flex-shrink-0 h-full border-r border-outline-variant opacity-20" 
                  />
                ))}
              </div>

              <div className="relative z-10">
                {rooms.map(room => (
                  <RoomRow
                    key={`row-${room.id}`}
                    room={room}
                    allocations={roomAllocations[room.id] ?? EMPTY_ALLOCATIONS}
                    onDragOver={handleDragOver}
                    onDrop={handleDrop}
                    monthStart={monthStart}
                    monthEnd={monthEnd}
                    nextMonthStart={nextMonthStart}
                    CELL_WIDTH={CELL_WIDTH}
                  />
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </M3Card>
  );
});

PlanningBoard.displayName = 'PlanningBoard';

export default PlanningBoard;
