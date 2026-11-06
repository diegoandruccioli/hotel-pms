import type { RoomStatus } from '../../types';

export const STATUS_KEYS: Record<RoomStatus, string> = {
  CLEAN: 'room_status_clean',
  DIRTY: 'room_status_dirty',
  MAINTENANCE: 'room_status_maintenance',
  OCCUPIED: 'room_status_occupied',
};

/** Statuses housekeeping can set by hand (OCCUPIED follows the stay). */
export const ALL_STATUSES: RoomStatus[] = ['CLEAN', 'DIRTY', 'MAINTENANCE'];
