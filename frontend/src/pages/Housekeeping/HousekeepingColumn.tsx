import { memo, useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { M3Card, M3EmptyState } from '../../components/m3';
import type { RoomResponse, RoomStatus } from '../../types';
import { cn, roomStatusTone, toneDotClasses } from '../../utils';
import { RoomCard } from './RoomCard';
import { STATUS_KEYS } from './roomStatus';

interface HousekeepingColumnProps {
  status: RoomStatus;
  rooms: RoomResponse[];
  /** Only statuses housekeeping can set by hand accept a drop (OCCUPIED follows the stay). */
  droppable: boolean;
  selectedIds: ReadonlySet<string>;
  priorityRooms: ReadonlySet<string>;
  /** Rooms with a status change in flight. */
  busyIds: ReadonlySet<string>;
  focusRoomId: string | null;
  onFocusHandled: () => void;
  onToggleSelected: (id: string) => void;
  onStatusChange: (id: string, status: RoomStatus) => Promise<void>;
  /** Called with the dragged room id when it is dropped on this column. */
  onDropRoom: (roomId: string, status: RoomStatus) => void;
}

/** One status column of the housekeeping board; a drop target when `droppable`. */
export const HousekeepingColumn = memo(({
  status, rooms, droppable, selectedIds, priorityRooms, busyIds, focusRoomId, onFocusHandled, onToggleSelected, onStatusChange, onDropRoom,
}: HousekeepingColumnProps) => {
  const { t } = useTranslation('common');
  const [isDragOver, setIsDragOver] = useState(false);
  const titleId = `housekeeping-column-${status}`;

  const handleDragOver = useCallback((e: React.DragEvent) => {
    if (!droppable) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    setIsDragOver(true);
  }, [droppable]);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    // Moving over a child fires dragleave on the column too: only clear when truly leaving it.
    if (e.currentTarget.contains(e.relatedTarget as Node | null)) return;
    setIsDragOver(false);
  }, []);

  const handleDrop = useCallback((e: React.DragEvent) => {
    if (!droppable) return;
    e.preventDefault();
    setIsDragOver(false);
    const roomId = e.dataTransfer.getData('text/plain');
    if (roomId) onDropRoom(roomId, status);
  }, [droppable, onDropRoom, status]);

  return (
    <M3Card
      variant="solid"
      role="group"
      aria-labelledby={titleId}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      className={cn(
        'relative flex flex-col gap-3 overflow-hidden p-3 pt-4',
        isDragOver && 'outline outline-2 outline-primary bg-primary-container/30',
      )}
    >
      <div aria-hidden="true" className={cn('absolute inset-x-0 top-0 h-1', toneDotClasses[roomStatusTone[status]])} />
      <div className="flex items-center justify-between gap-2 px-1">
        <h2 id={titleId} className="text-sm font-display font-semibold text-on-surface">{t(STATUS_KEYS[status])}</h2>
        <span className="rounded-full bg-surface-container-high px-2 py-0.5 text-xs font-medium font-body text-on-surface-variant">
          <span aria-hidden="true">{rooms.length}</span>
          <span className="sr-only">{t('housekeeping_column_count', { count: rooms.length })}</span>
        </span>
      </div>
      {rooms.length === 0 ? (
        <M3EmptyState icon="cleaning_services" title={t('housekeeping_column_empty')} className="py-6" />
      ) : (
        <div className="flex flex-col gap-3">
          {rooms.map((room) => (
            <RoomCard
              key={room.id}
              room={room}
              selected={selectedIds.has(room.id)}
              priority={status === 'DIRTY' && priorityRooms.has(room.roomNumber)}
              busy={busyIds.has(room.id)}
              focusRequested={focusRoomId === room.id}
              onFocusHandled={onFocusHandled}
              onToggleSelected={onToggleSelected}
              onStatusChange={onStatusChange}
            />
          ))}
        </div>
      )}
    </M3Card>
  );
});

HousekeepingColumn.displayName = 'HousekeepingColumn';
