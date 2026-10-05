import { useState, useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import type { RoomResponse, RoomStatus } from '../../types';
import { useFormatters } from '../../hooks';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Card, M3Checkbox, M3StatusChip } from '../../components/m3';
import { cn, roomStatusTone, toneDotClasses, toneOutlineButtonClasses } from '../../utils';
import { STATUS_KEYS, ALL_STATUSES } from './roomStatus';

const StatusButton = memo(({ newStatus, updating, onClick, t }: {
  newStatus: RoomStatus;
  updating: RoomStatus | null;
  onClick: (s: RoomStatus) => void;
  t: (k: string) => string;
}) => {
  const handleClick = useCallback(() => {
    onClick(newStatus);
  }, [onClick, newStatus]);

  return (
    <button
      onClick={handleClick}
      disabled={updating !== null}
      className={cn(
        'flex-1 flex items-center justify-center min-h-10 text-xs font-medium font-body border rounded-shape-sm px-3 py-1.5 transition-colors disabled:opacity-50 disabled:cursor-not-allowed',
        toneOutlineButtonClasses[roomStatusTone[newStatus]]
      )}
    >
      {updating === newStatus ? (
        <span className="flex items-center justify-center gap-1">
          <MaterialIcon name="progress_activity" size={12} className="animate-spin" />
          {t('saving')}
        </span>
      ) : (
        `→ ${t(STATUS_KEYS[newStatus])}`
      )}
    </button>
  );
});

StatusButton.displayName = 'StatusButton';

interface RoomCardProps {
  room: RoomResponse;
  selected: boolean;
  onToggleSelected: (id: string) => void;
  onStatusChange: (id: string, status: RoomStatus) => Promise<void>;
}

export const RoomCard = memo(({ room, selected, onToggleSelected, onStatusChange }: RoomCardProps) => {
  const { t } = useTranslation('common');
  const [updating, setUpdating] = useState<RoomStatus | null>(null);

  const { formatCurrency } = useFormatters();

  const handleStatusButton = useCallback(async (newStatus: RoomStatus) => {
    if (newStatus === room.status) return;
    setUpdating(newStatus);
    try {
      await onStatusChange(room.id, newStatus);
    } finally {
      setUpdating(null);
    }
  }, [room.status, room.id, onStatusChange]);

  const handleToggle = useCallback(() => {
    onToggleSelected(room.id);
  }, [onToggleSelected, room.id]);

  return (
    <M3Card variant="solid" className="relative flex flex-col gap-3 overflow-hidden p-4">
      <div
        data-testid="room-card-tone"
        aria-hidden="true"
        className={cn('absolute inset-x-0 top-0 h-1', toneDotClasses[roomStatusTone[room.status]])}
      />
      {/* flex-wrap: the checkbox + room-number block + status chip can outgrow a
          4-up grid column for the longer status labels (e.g. "Maintenance").
          Without wrapping, the flex-1 min-w-16 heading block was the only shrinkable
          item and got squeezed to zero width — the room number disappeared
          entirely instead of just re-flowing the chip onto its own line. */}
      <div className="flex items-start justify-between gap-2 flex-wrap">
        {room.status !== 'OCCUPIED' && (
          <M3Checkbox
            label={t('select_room', { number: room.roomNumber })}
            className="[&>label]:sr-only"
            checked={selected}
            onChange={handleToggle}
          />
        )}
        <div className="flex-1 min-w-16">
          <h3 className="text-lg font-display font-bold text-on-surface truncate">{t('room_number', { number: room.roomNumber })}</h3>
          <p className="text-xs font-body font-medium uppercase tracking-wide text-on-surface-variant truncate">{room.roomType?.name}</p>
        </div>
        <M3StatusChip label={t(STATUS_KEYS[room.status])} tone={roomStatusTone[room.status]} />
      </div>

      <p className="text-sm font-body text-on-surface-variant">{formatCurrency(room.roomType?.basePrice)} / {t('night')}</p>

      {room.status !== 'OCCUPIED' && (
        <div className="flex gap-2 flex-wrap mt-1">
          {ALL_STATUSES.filter((s) => s !== room.status).map((newStatus) => (
            <StatusButton
              key={newStatus}
              newStatus={newStatus}
              updating={updating}
              onClick={handleStatusButton}
              t={t}
            />
          ))}
        </div>
      )}
    </M3Card>
  );
});

RoomCard.displayName = 'RoomCard';
