import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { reservationService } from '../../services/reservationService';
import { inventoryService } from '../../services/inventoryService';
import type { ReservationResponse } from '../../types';
import type { SpringPage } from '../../types';
import { queryKeys } from '../../lib/queryKeys';

interface SearchParams {
  query: string;
  upcomingOnly: boolean;
  page: number;
  size: number;
  sort: string;
}

export function useReservationsSearch(params: SearchParams) {
  return useQuery({
    queryKey: queryKeys.reservations.search(params),
    queryFn: () => reservationService.searchReservations(params),
    placeholderData: (previousData) => previousData,
  });
}

/** All rooms, used only to resolve room numbers for the reservations table
 * (`li.roomId` -> `roomNumber`) — see `queryKeys.rooms.lookup`. */
export function useRoomsLookup() {
  return useQuery({
    queryKey: queryKeys.rooms.lookup,
    queryFn: () => inventoryService.getAllRooms(0, 500).then((page) => page.content),
  });
}

export function useDeleteReservation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => reservationService.deleteReservation(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.reservations.all });
    },
  });
}

/** Patches the single updated reservation into whichever cached search-result
 * page(s) it appears in, instead of invalidating — this only clears an
 * email-failed badge on one row and doesn't warrant refetching the current
 * filtered/sorted page (same reasoning as useUpdateRoomStatus). */
export function useRetryConfirmationEmail() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => reservationService.retryConfirmationEmail(id),
    onSuccess: (updated) => {
      queryClient.setQueriesData<SpringPage<ReservationResponse>>(
        { queryKey: queryKeys.reservations.all },
        (old) => old && { ...old, content: old.content.map((r) => (r.id === updated.id ? updated : r)) },
      );
    },
  });
}
