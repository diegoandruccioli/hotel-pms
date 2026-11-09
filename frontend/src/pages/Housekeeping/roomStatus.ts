import type { RoomStatus } from '../../types';

export const STATUS_KEYS: Record<RoomStatus, string> = {
  CLEAN: 'room_status_clean',
  DIRTY: 'room_status_dirty',
  MAINTENANCE: 'room_status_maintenance',
  OCCUPIED: 'room_status_occupied',
};

/** Statuses housekeeping can set by hand (OCCUPIED follows the stay). */
export const ALL_STATUSES: RoomStatus[] = ['CLEAN', 'DIRTY', 'MAINTENANCE'];

/** Value of the board's status filter: one status, or every room. */
export type StatusFilter = 'ALL' | RoomStatus;

/** Order of the filter buttons; same order as the board columns. */
export const FILTER_OPTIONS: ReadonlyArray<{ value: StatusFilter; labelKey: string }> = [
  { value: 'ALL', labelKey: 'housekeeping_filter_all' },
  { value: 'DIRTY', labelKey: 'housekeeping_filter_dirty' },
  { value: 'CLEAN', labelKey: 'housekeeping_filter_clean' },
  { value: 'MAINTENANCE', labelKey: 'housekeeping_filter_maintenance' },
  { value: 'OCCUPIED', labelKey: 'housekeeping_filter_occupied' },
];

/** Reads the `?status=` value: anything that is not a known status means "no filter". */
export function parseStatusFilter(raw: string | null): StatusFilter {
  return FILTER_OPTIONS.some((o) => o.value === raw) ? (raw as StatusFilter) : 'ALL';
}
