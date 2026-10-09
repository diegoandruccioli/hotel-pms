import { useCallback, useMemo, memo } from 'react';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3EmptyState } from '../../components/m3';
import { useTranslation } from 'react-i18next';
import type { RoomResponse } from '../../types';
import type { ReservationResponse } from '../../types';
import { cn } from '../../utils';

interface RoomButtonProps {
  room: RoomResponse;
  isSelected: boolean;
  isOccupied: boolean;
  readOnly: boolean;
  resolvedTotalPrice?: number;
  onToggle: (id: string) => void;
}

const RoomButton = memo(({ room, isSelected, isOccupied, readOnly, resolvedTotalPrice, onToggle }: RoomButtonProps) => {
  const { t } = useTranslation(['reservations', 'common']);

  // An occupied room cannot be picked, but one that is already selected (dates changed
  // after choosing it) must stay clickable so the user can untick it.
  const blocked = isOccupied && !isSelected;

  const handleClick = useCallback(() => {
    if (!readOnly && !blocked) {
      onToggle(room.id);
    }
  }, [readOnly, blocked, onToggle, room.id]);

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={blocked}
      aria-label={isOccupied ? `${t('common:room_number', { number: room.roomNumber })} — ${t('common:room_occupied')}` : undefined}
      className={cn(
        'p-3 rounded-shape-sm border text-left transition-colors flex flex-col gap-1',
        isOccupied
          ? cn('bg-surface-variant border-outline-variant', blocked ? 'opacity-40 cursor-not-allowed' : 'border-error')
          : isSelected
            ? 'bg-primary/10 border-primary shadow-xs'
            : 'border-outline-variant hover:border-outline',
        readOnly && !isOccupied && 'cursor-default'
      )}
    >
      <div className="flex justify-between items-center w-full">
        <span className={cn('font-medium', isOccupied ? 'text-on-surface-variant' : isSelected ? 'text-primary' : 'text-on-surface')}>
          {t('common:room_number', { number: room.roomNumber })}
        </span>
        {isOccupied
          ? <MaterialIcon name="block" size={16} className="text-on-surface-variant" />
          : isSelected && <MaterialIcon name="check_circle" size={16} className="text-primary" />
        }
      </div>
      <span className="text-xs text-on-surface-variant">{room.roomType?.name || room.type}</span>
      <span className={cn('text-sm font-medium mt-1', isOccupied && 'text-on-surface-variant')}>
        {resolvedTotalPrice !== undefined
          ? t('reservations:price_total_stay', { amount: resolvedTotalPrice })
          : `€${room.roomType?.basePrice}`}
      </span>
      {isOccupied && (
        <span className="text-xs text-on-surface-variant italic">{t('common:room_occupied')}</span>
      )}
    </button>
  );
});

RoomButton.displayName = 'RoomButton';

interface RoomGridProps {
  checkInDate: string;
  checkOutDate: string;
  availableRooms: RoomResponse[];
  selectedRoomIds: string[];
  allReservations: ReservationResponse[];
  currentReservationId?: string;
  /** roomId -> resolved total price for the selected dates (RatePricingService). */
  resolvedPrices?: Map<string, number>;
  onToggleRoom: (id: string) => void;
  readOnly?: boolean;
}

export const RoomGrid = memo(({
  checkInDate,
  checkOutDate,
  availableRooms,
  selectedRoomIds,
  allReservations,
  currentReservationId,
  resolvedPrices,
  onToggleRoom,
  readOnly = false
}: RoomGridProps) => {
  const { t } = useTranslation(['reservations', 'common']);

  const occupiedRoomIds = useMemo<Set<string>>(() => {
    if (!checkInDate || !checkOutDate) return new Set();
    const newIn = new Date(checkInDate).getTime();
    const newOut = new Date(checkOutDate).getTime();
    const occupied = new Set<string>();
    allReservations.forEach(r => {
      if (r.id === currentReservationId) return;
      if (r.active === false || r.status === 'CANCELLED') return;
      const rIn = new Date(r.checkInDate).getTime();
      const rOut = new Date(r.checkOutDate).getTime();
      if (newIn < rOut && newOut > rIn) {
        r.lineItems.forEach(li => {
          if (li.active !== false) occupied.add(li.roomId);
        });
      }
    });
    return occupied;
  }, [checkInDate, checkOutDate, allReservations, currentReservationId]);

  return (
    <div>
      <h3 className="text-sm font-medium text-on-surface-variant uppercase tracking-wider mb-3">{t('select_rooms')}</h3>
      {availableRooms.length === 0 ? (
        <M3EmptyState icon="meeting_room" title={t('no_rooms_available')} className="py-6" />
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
          {availableRooms.map(room => (
            <RoomButton
              key={room.id}
              room={room}
              isSelected={selectedRoomIds.includes(room.id)}
              isOccupied={occupiedRoomIds.has(room.id)}
              readOnly={readOnly}
              resolvedTotalPrice={resolvedPrices?.get(room.id)}
              onToggle={onToggleRoom}
            />
          ))}
        </div>
      )}
    </div>
  );
});

RoomGrid.displayName = 'RoomGrid';
