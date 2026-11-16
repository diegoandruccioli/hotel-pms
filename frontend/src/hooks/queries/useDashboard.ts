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

/** Night-audit snapshots of the days before today. `null` data means the
 * backend has no such endpoint (404); `points` can be empty or have gaps, so
 * callers show a delta/sparkline only when there are at least two points. */
export function useDaySheetTrend(days = 7) {
  return useQuery({
    queryKey: queryKeys.dashboard.daySheetTrend(todayIsoDate(), days),
    queryFn: () => dashboardService.getDaySheetTrend(days),
  });
}

/** The hotel's full operating history: what Dashboard and Billing both ask for
 * when a number must not depend on the month (e.g. what is still to collect).
 * One shared range means one cache entry for both screens. */
export const ALL_TIME_SUMMARY_START = '2000-01-01';
export const ALL_TIME_SUMMARY_END = '2099-12-31';

/** `enabled: false` for RECEPTIONIST — the owner-summary endpoint is
 * role-gated server-side and would just 403. */
export function useOwnerFinancialSummary(startDate: string, endDate: string, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.ownerReport.summary(startDate, endDate),
    queryFn: () => billingReportService.getOwnerFinancialSummary(startDate, endDate),
    enabled,
  });
}
