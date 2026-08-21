import { useQuery } from '@tanstack/react-query';
import { guestService } from '../../services/guestService';
import { reservationService } from '../../services/reservationService';
import { queryKeys } from '../../lib/queryKeys';

const RESULT_LIMIT = 5;
/** Below this length a query is too short to be worth a request — avoids
 * firing on a single keystroke right after the palette opens. */
const MIN_QUERY_LENGTH = 2;

/** Top guest matches for the command palette's search-as-you-type section.
 * `query` is expected to already be debounced by the caller (≥300ms, same
 * convention as every other search box in the app). */
export function useCommandPaletteGuests(query: string) {
  const trimmed = query.trim();
  return useQuery({
    queryKey: queryKeys.commandPalette.guests(trimmed),
    queryFn: () => guestService.searchGuestsPaged(trimmed, 0, RESULT_LIMIT),
    enabled: trimmed.length >= MIN_QUERY_LENGTH,
    staleTime: 30_000,
  });
}

/** Top reservation matches (by guest name/email) for the command palette. */
export function useCommandPaletteReservations(query: string) {
  const trimmed = query.trim();
  return useQuery({
    queryKey: queryKeys.commandPalette.reservations(trimmed),
    queryFn: () => reservationService.searchReservations({
      query: trimmed,
      page: 0,
      size: RESULT_LIMIT,
      sort: 'checkInDate,desc',
    }),
    enabled: trimmed.length >= MIN_QUERY_LENGTH,
    staleTime: 30_000,
  });
}
