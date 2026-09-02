import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { stayService } from '../../services/stayService';
import type { StayResponse } from '../../types';
import type { SpringPage } from '../../types';
import { queryKeys } from '../../lib/queryKeys';

export function useStaysList(page: number) {
  return useQuery({
    queryKey: queryKeys.stays.list(page),
    queryFn: () => stayService.getAllStays(page),
    placeholderData: (previousData) => previousData,
  });
}

/** checkOut/retryInvoiceCreation/retryCheckoutEmail all patch the single
 * updated stay into the cached page in place — same reasoning as the
 * equivalent room-status and reservation-email mutations: these are
 * per-row actions on the row already visible, not something that needs
 * (or benefits from) refetching the whole page. */
function useStayPatchMutation(action: (id: string) => Promise<StayResponse>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => action(id),
    onSuccess: (updated) => {
      queryClient.setQueriesData<SpringPage<StayResponse>>(
        { queryKey: queryKeys.stays.all },
        (old) => old && { ...old, content: old.content.map((s) => (s.id === updated.id ? updated : s)) },
      );
    },
  });
}

export function useCheckOutStay() {
  return useStayPatchMutation(stayService.checkOut);
}

export function useRetryInvoiceCreation() {
  return useStayPatchMutation(stayService.retryInvoiceCreation);
}

export function useRetryCheckoutEmail() {
  return useStayPatchMutation(stayService.retryCheckoutEmail);
}
