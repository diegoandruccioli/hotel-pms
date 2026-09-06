import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { reservationGroupService } from '../../services';
import { queryKeys } from '../../lib';
import type { ReservationGroupCreateRequest } from '../../types';

export function useReservationGroups(page: number, size = 20) {
  return useQuery({
    queryKey: queryKeys.reservationGroups.list(page, size),
    queryFn: () => reservationGroupService.getAllGroups(page, size),
    placeholderData: (previousData) => previousData,
  });
}

export function useReservationGroup(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.reservationGroups.detail(id ?? ''),
    queryFn: () => reservationGroupService.getGroup(id as string),
    enabled: Boolean(id),
  });
}

export function useCreateReservationGroup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: ReservationGroupCreateRequest) => reservationGroupService.createGroup(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.reservationGroups.all });
      queryClient.invalidateQueries({ queryKey: queryKeys.reservations.all });
    },
  });
}

export function useCancelReservationGroup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version?: number | null }) =>
      reservationGroupService.cancelGroup(id, version),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.reservationGroups.all });
      queryClient.invalidateQueries({ queryKey: queryKeys.reservations.all });
    },
  });
}

export function useCheckoutReservationGroup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => reservationGroupService.checkoutGroup(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.reservationGroups.detail(id) });
      queryClient.invalidateQueries({ queryKey: queryKeys.reservationGroups.all });
      queryClient.invalidateQueries({ queryKey: queryKeys.stays.all });
    },
  });
}
