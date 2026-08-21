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
