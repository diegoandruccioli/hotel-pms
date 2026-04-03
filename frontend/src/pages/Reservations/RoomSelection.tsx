import { useCallback, memo } from 'react';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3TextField } from '../../components/m3/M3TextField';
import { useTranslation } from 'react-i18next';
import type { RoomResponse } from '../../types/inventory.types';

interface RoomButtonProps {
  room: RoomResponse;
  isSelected: boolean;
  readOnly: boolean;
  onToggle: (id: string) => void;
}

const RoomButton = memo(({ room, isSelected, readOnly, onToggle }: RoomButtonProps) => {
  const handleClick = useCallback(() => {
    if (!readOnly) {
      onToggle(room.id);
    }
  }, [readOnly, onToggle, room.id]);

  return (
    <button
      type="button"
      onClick={handleClick}
      className={`p-3 rounded-shape-sm border text-left transition-colors flex flex-col gap-1 ${
        isSelected 
          ? 'bg-primary/10 border-primary shadow-sm' 
          : 'border-outline-variant hover:border-outline'
      } ${readOnly ? 'cursor-default' : ''}`}
    >
      <div className="flex justify-between items-center w-full">
        <span className={`font-medium ${isSelected ? 'text-primary' : 'text-on-surface'}`}>
          Room {room.roomNumber}
        </span>
        {isSelected && <MaterialIcon name="check_circle" size={16} className="text-primary" />}
      </div>
      <span className="text-xs text-on-surface-variant">{room.roomType?.name || room.type}</span>
      <span className="text-sm font-medium mt-1">€{room.pricePerNight ?? room.roomType?.basePrice}</span>
    </button>
  );
});

RoomButton.displayName = 'RoomButton';

interface RoomSelectionProps {
  checkInDate: string;
  checkOutDate: string;
  expectedGuests: number | string;
  availableRooms: RoomResponse[];
  selectedRoomIds: string[];
  onCheckInChange: (val: string) => void;
  onCheckOutChange: (val: string) => void;
  onExpectedGuestsChange: (val: number | string) => void;
  onToggleRoom: (id: string) => void;
  readOnly?: boolean;
}

export const RoomSelection = memo(({
  checkInDate,
  checkOutDate,
  expectedGuests,
  availableRooms,
  selectedRoomIds,
  onCheckInChange,
  onCheckOutChange,
  onExpectedGuestsChange,
  onToggleRoom,
  readOnly = false
}: RoomSelectionProps) => {

  const { t } = useTranslation('common');

  const handleCheckInChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    onCheckInChange(e.target.value);
  }, [onCheckInChange]);

  const handleCheckOutChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    onCheckOutChange(e.target.value);
  }, [onCheckOutChange]);

  const handleExpectedGuestsChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const val = parseInt(e.target.value);
    onExpectedGuestsChange(isNaN(val) ? '' : val);
  }, [onExpectedGuestsChange]);

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <M3TextField 
          label={t('label_checkin_date')}
          type="date" 
          value={checkInDate} 
          onChange={handleCheckInChange} 
          required 
          readOnly={readOnly}
        />
        <M3TextField 
          label={t('label_checkout_date')}
          type="date" 
          value={checkOutDate} 
          onChange={handleCheckOutChange} 
          required 
          readOnly={readOnly}
        />
        <M3TextField 
          label={t('label_expected_guests')}
          type="number" 
          min="1"
          value={expectedGuests} 
          onChange={handleExpectedGuestsChange} 
          required 
          readOnly={readOnly}
        />
      </div>

      <div className="pt-4">
        <h3 className="text-sm font-medium text-on-surface-variant uppercase tracking-wider mb-3">{t('select_rooms')}</h3>
        {availableRooms.length === 0 ? (
          <p className="text-sm text-on-surface-variant">No available rooms loaded.</p>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
            {availableRooms.map(room => (
              <RoomButton 
                key={room.id}
                room={room}
                isSelected={selectedRoomIds.includes(room.id)}
                readOnly={readOnly}
                onToggle={onToggleRoom}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
});

RoomSelection.displayName = 'RoomSelection';
