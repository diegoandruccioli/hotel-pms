import { useQuery } from '@tanstack/react-query';
import { dashboardService } from '../../services/dashboardService';
import { billingReportService } from '../../services/billingReportService';
import { queryKeys } from '../../lib/queryKeys';

const getTodayDateString = (): string => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

export function useDaySheet() {
  return useQuery({
    queryKey: queryKeys.dashboard.daySheet(getTodayDateString()),
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
