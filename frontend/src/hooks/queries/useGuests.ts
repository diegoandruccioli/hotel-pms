import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { guestService } from '../../services/guestService';
import { queryKeys } from '../../lib/queryKeys';

/**
 * Server-side-searched, paginated guest list. Replaces the manual
 * loading/error/useEffect trio that used to live in `pages/Guests.tsx` —
 * React Query owns the fetch, cache and in-flight/error state instead.
 */
export function useGuestsSearch(query: string, page: number, size = 20, sort?: string) {
  return useQuery({
    queryKey: queryKeys.guests.search(query, page, size, sort),
    queryFn: () => guestService.searchGuestsPaged(query, page, size, sort),
    placeholderData: (previousData) => previousData,
  });
}

export function useDeleteGuest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => guestService.deleteGuest(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.guests.all });
    },
  });
}
