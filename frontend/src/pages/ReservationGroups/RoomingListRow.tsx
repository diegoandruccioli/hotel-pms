import { memo, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button, M3Select, M3TextField, M3Checkbox } from '../../components/m3';
import { GuestSearchAndCreate } from '../Reservations/GuestSearchAndCreate';
import type { GuestResponseDTO, RoomResponse } from '../../types';

export interface RoomingListRowState {
  tempId: string;
  guest: GuestResponseDTO | null;
  roomId: string;
  expectedGuests: number | string;
  billedToMasterFolio: boolean;
}

interface RoomingListRowProps {
  row: RoomingListRowState;
  availableRooms: RoomResponse[];
  roomTakenElsewhere: (roomId: string) => boolean;
  openMasterFolio: boolean;
  onChange: (tempId: string, patch: Partial<RoomingListRowState>) => void;
  onRemove: (tempId: string) => void;
  canRemove: boolean;
}

export const RoomingListRow = memo(({
  row, availableRooms, roomTakenElsewhere, openMasterFolio, onChange, onRemove, canRemove,
}: RoomingListRowProps) => {
  const { t } = useTranslation('common');

  const handleSelectGuest = useCallback((guest: GuestResponseDTO) => {
    onChange(row.tempId, { guest });
  }, [onChange, row.tempId]);

  const handleClearGuest = useCallback(() => {
    onChange(row.tempId, { guest: null });
  }, [onChange, row.tempId]);

  const handleRoomChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    onChange(row.tempId, { roomId: e.target.value });
  }, [onChange, row.tempId]);

  const handleExpectedGuestsChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const val = parseInt(e.target.value, 10);
    onChange(row.tempId, { expectedGuests: Number.isNaN(val) ? '' : val });
  }, [onChange, row.tempId]);

  const handleBilledToggle = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    onChange(row.tempId, { billedToMasterFolio: e.target.checked });
  }, [onChange, row.tempId]);

  const handleRemove = useCallback(() => onRemove(row.tempId), [onRemove, row.tempId]);

  const roomOptions = availableRooms
    .filter((r) => r.id === row.roomId || !roomTakenElsewhere(r.id))
    .map((r) => ({ value: r.id, label: `${r.roomNumber} — ${r.roomType?.name ?? ''}` }));

  return (
    <div className="border border-outline-variant rounded-shape-md p-4 space-y-3">
      <div className="flex justify-between items-start gap-2">
        <div className="flex-1">
          <GuestSearchAndCreate
            selectedGuest={row.guest}
            onSelectGuest={handleSelectGuest}
            onClearGuest={handleClearGuest}
          />
        </div>
        {canRemove && (
          <M3Button
            type="button"
            variant="text"
            icon="delete"
            aria-label={t('remove_room')}
            onClick={handleRemove}
          />
        )}
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 items-end">
        <M3Select
          label={t('label_room')}
          options={roomOptions}
          placeholder={t('select_room_placeholder')}
          value={row.roomId}
          onChange={handleRoomChange}
          required
        />
        <M3TextField
          label={t('label_expected_guests')}
          type="number"
          min="1"
          value={row.expectedGuests}
          onChange={handleExpectedGuestsChange}
          required
        />
        {openMasterFolio && (
          <M3Checkbox
            label={t('billed_to_master_folio')}
            checked={row.billedToMasterFolio}
            onChange={handleBilledToggle}
          />
        )}
      </div>
      {roomOptions.length === 0 && (
        <p className="text-xs text-on-surface-variant flex items-center gap-1">
          <MaterialIcon name="info" size={14} />
          {t('no_rooms_available')}
        </p>
      )}
    </div>
  );
});

RoomingListRow.displayName = 'RoomingListRow';
