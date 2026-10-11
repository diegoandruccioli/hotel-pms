import { useMemo } from 'react';
import { useReservationsSearch } from '../../hooks/queries';
import type { ReservationResponse } from '../../types';

/** Rows shown per status; the backend still processes every candidate on the run. */
const NO_SHOW_CANDIDATES_PAGE_SIZE = 50;

const searchParams = (status: 'PENDING' | 'CONFIRMED', businessDate: string) => ({
  query: '',
  upcomingOnly: false,
  page: 0,
  size: NO_SHOW_CANDIDATES_PAGE_SIZE,
  dateTo: businessDate,
  status,
  sort: 'checkInDate,asc',
});

/**
 * Reservations the night audit would auto-mark as no-show for `businessDate`: the same set
 * the backend sweeps (PENDING and CONFIRMED, check-in on or before the date). The search
 * endpoint filters by a single status, hence two queries merged here.
 */
export function useNoShowCandidates(businessDate: string) {
  // An empty date would drop `dateTo` and list every future booking, so nothing is fetched then.
  const enabled = businessDate !== '';
  const pending = useReservationsSearch(searchParams('PENDING', businessDate), enabled);
  const confirmed = useReservationsSearch(searchParams('CONFIRMED', businessDate), enabled);

  const candidates = useMemo<ReservationResponse[]>(
    () => [...(pending.data?.content ?? []), ...(confirmed.data?.content ?? [])]
      .sort((a, b) => a.checkInDate.localeCompare(b.checkInDate)),
    [pending.data, confirmed.data],
  );
  const total = (pending.data?.totalElements ?? 0) + (confirmed.data?.totalElements ?? 0);

  return {
    candidates,
    total,
    // placeholderData keeps the previous date's rows while the new date loads: not trustworthy yet.
    isLoading: enabled && (pending.isLoading || confirmed.isLoading || pending.isPlaceholderData || confirmed.isPlaceholderData),
    error: pending.error ?? confirmed.error,
  };
}
