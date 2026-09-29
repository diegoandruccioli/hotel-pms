import { todayIsoDate } from '../../utils';
import { useQuery } from '@tanstack/react-query';
import { dashboardService } from '../../services';
import { billingReportService } from '../../services';
import { queryKeys } from '../../lib';

export function useDaySheet() {
  return useQuery({
    queryKey: queryKeys.dashboard.daySheet(todayIsoDate()),
    queryFn: () => dashboardService.getDaySheet(),
  });
}

/** `enabled: false` for RECEPTIONIST — the owner-summary endpoint is
 * role-gated server-side and would just 403. */
export function useOwnerFinancialSummary(startDate: string, endDate: string, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.ownerReport.summary(startDate, endDate),
    queryFn: () => billingReportService.getOwnerFinancialSummary(startDate, endDate),
    enabled,
  });
}
