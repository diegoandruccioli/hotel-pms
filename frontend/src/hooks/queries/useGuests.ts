import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { billingService, guestService, stayService } from '../../services';
import { queryKeys } from '../../lib';

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

export function useGuestById(id: string | null | undefined) {
  return useQuery({
    queryKey: queryKeys.guests.detail(id ?? ''),
    queryFn: () => guestService.getGuestById(id as string),
    enabled: !!id,
  });
}

/** Type-ahead guest lookup: idle for a blank query. */
export function useGuestSuggestions(query: string) {
  const trimmed = query.trim();
  return useQuery({
    queryKey: queryKeys.guests.suggest(trimmed),
    queryFn: () => guestService.searchGuests(trimmed),
    enabled: trimmed.length > 0,
  });
}

/** Stay history of a guest (primary guest only), for the detail sheet. */
export function useGuestStayHistory(id: string) {
  return useQuery({
    queryKey: queryKeys.guests.stays(id),
    queryFn: () => stayService.getGuestStayHistory(id),
  });
}

/** Invoice history of a guest; `enabled` lets roles without financial access skip the call. */
export function useGuestInvoiceHistory(id: string, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.guests.invoices(id),
    queryFn: () => billingService.getGuestInvoiceHistory(id),
    enabled,
  });
}
