import { memo, useMemo } from 'react';
import type { RoomResponse, RoomStatus } from '../../types';
import { HousekeepingColumn } from './HousekeepingColumn';
import { cn } from '../../utils';
import { ALL_STATUSES, type StatusFilter } from './roomStatus';

/** Column order: what needs doing first, then done, then out of service, then in-house. */
const COLUMNS: readonly RoomStatus[] = ['DIRTY', 'CLEAN', 'MAINTENANCE', 'OCCUPIED'];

interface HousekeepingBoardProps {
  rooms: RoomResponse[];
  /** Show only this status' column, at full width; `ALL` shows the four columns. */
  statusFilter: StatusFilter;
  selectedIds: ReadonlySet<string>;
  priorityRooms: ReadonlySet<string>;
  busyIds: ReadonlySet<string>;
  focusRoomId: string | null;
  onFocusHandled: () => void;
  onToggleSelected: (id: string) => void;
  onStatusChange: (id: string, status: RoomStatus) => Promise<void>;
  onDropRoom: (roomId: string, status: RoomStatus) => void;
}

/** Kanban of the rooms grouped by status; stacked below `lg` so it never scrolls sideways. */
export const HousekeepingBoard = memo(({
  rooms, statusFilter, selectedIds, priorityRooms, busyIds, focusRoomId, onFocusHandled, onToggleSelected, onStatusChange, onDropRoom,
}: HousekeepingBoardProps) => {
  const byStatus = useMemo(() => {
    const groups: Record<RoomStatus, RoomResponse[]> = { DIRTY: [], CLEAN: [], MAINTENANCE: [], OCCUPIED: [] };
    for (const room of rooms) groups[room.status].push(room);
    return groups;
  }, [rooms]);

  const columns = statusFilter === 'ALL' ? COLUMNS : [statusFilter];

  return (
    <div className={cn('grid grid-cols-1 gap-4 lg:items-start', statusFilter === 'ALL' && 'lg:grid-cols-4')}>
      {columns.map((status) => (
        <HousekeepingColumn
          key={status}
          status={status}
          rooms={byStatus[status]}
          droppable={ALL_STATUSES.includes(status)}
          selectedIds={selectedIds}
          priorityRooms={priorityRooms}
          busyIds={busyIds}
          focusRoomId={focusRoomId}
          onFocusHandled={onFocusHandled}
          onToggleSelected={onToggleSelected}
          onStatusChange={onStatusChange}
          onDropRoom={onDropRoom}
        />
      ))}
    </div>
  );
});

HousekeepingBoard.displayName = 'HousekeepingBoard';
