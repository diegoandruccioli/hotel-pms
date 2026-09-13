import { useQuery } from '@tanstack/react-query';
import { housekeepingService } from '../../services';
import { queryKeys } from '../../lib';

/**
 * The hotel's currently resolved housekeeping business date. This is
 * deliberately what the worksheet date picker defaults to — never a
 * client-computed "today" (`new Date()`), since the whole point of the
 * business-date resolver is that "today" isn't always what the calendar
 * says (e.g. before the hotel's configured cutoff hour, it's still
 * yesterday for the night shift).
 */
export function useBusinessDate() {
  return useQuery({
    queryKey: queryKeys.housekeeping.businessDate,
    queryFn: () => housekeepingService.getBusinessDate(),
  });
}

/**
 * The housekeeping worksheet for a given date — powers the on-screen
 * preview (row counts, provisional/definitive badge) shown before the user
 * commits to a PDF download. `enabled: !!date` since the date picker starts
 * empty until `useBusinessDate` resolves.
 */
export function useHousekeepingWorksheet(date: string | undefined) {
  return useQuery({
    queryKey: queryKeys.housekeeping.worksheet(date ?? ''),
    queryFn: () => housekeepingService.getWorksheet(date as string),
    enabled: !!date,
  });
}
