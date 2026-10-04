import type { RoomStatus } from './inventory.types';

/**
 * Front-desk day-sheet summary — mirrors `DaySheetResponse` in
 * frontdesk-service. Replaces five separate client requests (reservations,
 * stays, rooms, availability, and the full owner financial report) the
 * Dashboard previously made to compute the same numbers in the browser.
 */
export interface DaySheetResponse {
  date: string;
  todayArrivals: number;
  todayDepartures: number;
  guestsInHouse: number;
  currentStays: number;
  availableRooms: number;
  /** Active room count per housekeeping status; a status with no rooms in
   * that state is absent from the map rather than present with 0. */
  roomStatusCounts: Partial<Record<RoomStatus, number>>;
}

/** One night-audit snapshot — mirrors `DaySheetTrendPoint` in frontdesk-service.
 * Taken at the audit (03:30 the next morning): `arrivals` counts expected
 * arrivals including no-shows, `guestsInHouse` those checked in at that moment. */
export interface DaySheetTrendPoint {
  date: string;
  arrivals: number;
  departures: number;
  guestsInHouse: number;
  availableRooms: number;
}

/** Recent snapshots, oldest first — mirrors `DaySheetTrendResponse`. A date
 * with no completed audit is absent, so `points` can have gaps or be empty;
 * today's value is the live day-sheet, never part of this list. */
export interface DaySheetTrendResponse {
  from: string;
  to: string;
  points: DaySheetTrendPoint[];
}
