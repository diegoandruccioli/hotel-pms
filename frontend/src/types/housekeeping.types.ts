import type { RoomStatus } from './inventory.types';

/** Mirrors `HousekeepingTaskType` in frontdesk-service. */
export type HousekeepingTaskType = 'DEPARTURE' | 'STAYOVER' | 'ARRIVAL_PREP' | 'VACANT_DIRTY' | 'MAINTENANCE';

/**
 * One room's worth of housekeeping worksheet detail — mirrors `HousekeepingRow`
 * in frontdesk-service. Deliberately carries no guest name (GDPR data
 * minimization — this can end up on a printed sheet).
 */
export interface HousekeepingRow {
  roomNumber: string;
  roomTypeName: string;
  taskType: HousekeepingTaskType;
  currentStatus: RoomStatus;
  pax: number;
  expectedDepartureDate: string | null;
  turnover: boolean;
  departureDateUnknown: boolean;
}

/**
 * The housekeeping worksheet for one hotel and one business date — mirrors
 * `HousekeepingWorksheetResponse` in frontdesk-service.
 */
export interface HousekeepingWorksheetResponse {
  date: string;
  generatedAt: string;
  /** `true` while the prior day's night audit hasn't completed yet — these
   * rows can still change (late check-outs, same-day bookings, no-shows). */
  provisional: boolean;
  hotelName: string | null;
  rows: HousekeepingRow[];
  summary: Partial<Record<HousekeepingTaskType, number>>;
}

/**
 * The hotel's currently resolved housekeeping business date — mirrors
 * `BusinessDateResponse` in frontdesk-service. Lets the date picker default
 * to the right day (e.g. before 04:00 local time, still yesterday) without
 * re-implementing the cutoff rule in the frontend.
 */
export interface BusinessDateResponse {
  businessDate: string;
  timezone: string;
  cutoffHour: number;
}
