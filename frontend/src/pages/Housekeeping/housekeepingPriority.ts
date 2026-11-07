import type { HousekeepingRow } from '../../types';

/**
 * Room numbers that should be cleaned first today: a same-day turnover (the departing
 * guest's room is re-let) or a room being prepared for an arrival. Derived from the
 * worksheet rows, which already carry both flags, so no extra backend data is needed.
 */
export function priorityRoomNumbers(rows: readonly HousekeepingRow[]): ReadonlySet<string> {
  return new Set(
    rows.filter((r) => r.turnover || r.taskType === 'ARRIVAL_PREP').map((r) => r.roomNumber),
  );
}
