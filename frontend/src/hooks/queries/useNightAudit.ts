import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { nightAuditService } from '../../services';
import { queryKeys } from '../../lib';

export function useNightAuditHistory(page: number, size = 20) {
  return useQuery({
    queryKey: queryKeys.nightAudit.history(page, size),
    queryFn: () => nightAuditService.getHistory(page, size),
    placeholderData: (previousData) => previousData,
  });
}

export function useRunNightAudit() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (date: string) => nightAuditService.run(date),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.nightAudit.all });
      // A COMPLETED run may have auto-marked no-shows — those reservations'
      // status changed server-side, same reasoning as useUpdateReservationStatus.
      queryClient.invalidateQueries({ queryKey: queryKeys.reservations.all });
    },
  });
}
