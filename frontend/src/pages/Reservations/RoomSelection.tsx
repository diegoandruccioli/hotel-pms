import { memo } from 'react';
import type { RoomResponse } from '../../types';
import type { ReservationResponse } from '../../types';
import { StayDatesFields } from './StayDatesFields';
import { RoomGrid } from './RoomGrid';

interface RoomSelectionProps {
  checkInDate: string;
  checkOutDate: string;
  expectedGuests: number | string;
  availableRooms: RoomResponse[];
  selectedRoomIds: string[];
  allReservations: ReservationResponse[];
  currentReservationId?: string;
  /** roomId -> resolved total price for the selected dates (RatePricingService). */
  resolvedPrices?: Map<string, number>;
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
  allReservations,
  currentReservationId,
  resolvedPrices,
  onCheckInChange,
  onCheckOutChange,
  onExpectedGuestsChange,
  onToggleRoom,
  readOnly = false
}: RoomSelectionProps) => (
  <div className="space-y-4">
    <StayDatesFields
      checkInDate={checkInDate}
      checkOutDate={checkOutDate}
      expectedGuests={expectedGuests}
      onCheckInChange={onCheckInChange}
      onCheckOutChange={onCheckOutChange}
      onExpectedGuestsChange={onExpectedGuestsChange}
      readOnly={readOnly}
    />
    <div className="pt-4">
      <RoomGrid
        checkInDate={checkInDate}
        checkOutDate={checkOutDate}
        availableRooms={availableRooms}
        selectedRoomIds={selectedRoomIds}
        allReservations={allReservations}
        currentReservationId={currentReservationId}
        resolvedPrices={resolvedPrices}
        onToggleRoom={onToggleRoom}
        readOnly={readOnly}
      />
    </div>
  </div>
));

RoomSelection.displayName = 'RoomSelection';
