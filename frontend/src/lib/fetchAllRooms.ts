import { inventoryService } from '../services/inventoryService';
import type { RoomResponse } from '../types';

/** Rooms per request when loading the whole hotel; the backend returns them sorted by room number. */
const ROOMS_FETCH_PAGE_SIZE = 200;

/**
 * Every active room of the hotel, not just the first page: reads page 0, then the remaining
 * pages in parallel. Use this wherever the whole list is needed (boards, lookups by id, room
 * pickers) so a hotel with more rooms than one page never silently loses the rest.
 */
export async function fetchAllRooms(): Promise<RoomResponse[]> {
  const first = await inventoryService.getAllRooms(0, ROOMS_FETCH_PAGE_SIZE);
  const extraPages = Math.max(0, (first.totalPages || 1) - 1);
  if (extraPages === 0) return first.content;
  const rest = await Promise.all(
    Array.from({ length: extraPages }, (_, i) => inventoryService.getAllRooms(i + 1, ROOMS_FETCH_PAGE_SIZE)),
  );
  return [...first.content, ...rest.flatMap((p) => p.content)];
}
