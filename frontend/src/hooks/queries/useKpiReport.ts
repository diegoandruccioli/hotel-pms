import { useQuery } from '@tanstack/react-query';
import { kpiReportService } from '../../services/kpiReportService';
import { queryKeys } from '../../lib';
import type { ReportGranularity } from '../../types';

/** Powers the RevPAR/ADR/Occupancy trend charts on `OwnerDashboard.tsx`.
 * `enabled` mirrors `useOwnerFinancialSummary`'s convention — the caller
 * gates it on the same ADMIN/OWNER role check the page already does. */
export function useKpiReport(
  startDate: string,
  endDate: string,
  granularity: ReportGranularity,
  enabled: boolean
) {
  return useQuery({
    queryKey: queryKeys.kpiReport.trend(startDate, endDate, granularity),
    queryFn: () => kpiReportService.getKpiReport(startDate, endDate, granularity),
    enabled,
  });
}
