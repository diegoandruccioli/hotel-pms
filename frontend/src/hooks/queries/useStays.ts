import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { stayService } from '../../services';
import type { StayResponse } from '../../types';
import type { SpringPage } from '../../types';
import { queryKeys } from '../../lib';

export function useStaysList(page: number) {
  return useQuery({
    queryKey: queryKeys.stays.list(page),
    queryFn: () => stayService.getAllStays(page),
    placeholderData: (previousData) => previousData,
  });
}

/** Powers the night-audit pre-check and the front-desk dashboard's due-out
 * widget — a small, status-filtered slice rather than the full paginated
 * list `useStaysList` backs. */
export function useStaysSearch(params: { status?: string; page: number; size?: number }) {
  return useQuery({
    queryKey: queryKeys.stays.search(params),
    queryFn: () => stayService.searchStays(params),
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

/** One stay with its guest list; the query stays idle while no stay is selected. */
export function useStayDetail(stayId: string | null) {
  return useQuery({
    queryKey: queryKeys.stays.detail(stayId ?? ''),
    queryFn: () => stayService.getStayById(stayId as string),
    enabled: !!stayId,
  });
}

/** Alloggiati state and document-type code tables — static reference data, fetched once per session. */
export function useAlloggiatiLookups(enabled: boolean) {
  const stati = useQuery({
    queryKey: queryKeys.stays.lookupStati,
    queryFn: () => stayService.getLookupStati(),
    staleTime: Infinity,
    enabled,
  });
  const tipdoc = useQuery({
    queryKey: queryKeys.stays.lookupTipdoc,
    queryFn: () => stayService.getLookupTipdoc(),
    staleTime: Infinity,
    enabled,
  });
  return { stati, tipdoc };
}
