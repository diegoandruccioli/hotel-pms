import type { ReservationStatus } from '../../types';

/** Quick filters of the reservations list; exactly one is active at a time. */
export type ReservationPreset = 'all' | 'upcoming' | 'pending' | 'arrivalsToday' | 'inHouse' | 'cancelled';

export const RESERVATION_PRESETS: readonly ReservationPreset[] =
  ['all', 'upcoming', 'pending', 'arrivalsToday', 'inHouse', 'cancelled'];

export interface ReservationFilterParams {
  upcomingOnly?: boolean;
  status?: ReservationStatus;
  dateFrom?: string;
  dateTo?: string;
}

/** Search params a preset stands for. The backend takes a single status, so
 * "arrivals today" means "check-in date is today", whatever the status. */
export const reservationFilterParams = (preset: ReservationPreset, today: string): ReservationFilterParams => {
  switch (preset) {
    case 'upcoming': return { upcomingOnly: true };
    case 'pending': return { status: 'PENDING' };
    case 'arrivalsToday': return { dateFrom: today, dateTo: today };
    case 'inHouse': return { status: 'CHECKED_IN' };
    case 'cancelled': return { status: 'CANCELLED' };
    default: return {};
  }
};

export type ReservationSortField = 'checkInDate' | 'checkOutDate' | 'status';
export type ReservationSortDir = 'asc' | 'desc';

const SORT_FIELDS: readonly ReservationSortField[] = ['checkInDate', 'checkOutDate', 'status'];
export const DEFAULT_SORT_FIELD: ReservationSortField = 'checkInDate';
export const DEFAULT_SORT_DIR: ReservationSortDir = 'desc';

export interface ReservationListFilters {
  preset: ReservationPreset;
  sortField: ReservationSortField;
  sortDir: ReservationSortDir;
}

/** Older entry points (dashboard, stays) passed the filters as navigation state. */
export type ReservationsNavState = Partial<ReservationListFilters>;

/**
 * Reads the list filters from the query string (`?preset=&sort=&dir=`). A missing or
 * unknown value is ignored in silence and falls back to the navigation state, then to
 * the default: a hand-edited URL is an approximation of intent, not a mistake.
 */
export const readReservationFilters = (
  params: URLSearchParams,
  nav: ReservationsNavState | null,
): ReservationListFilters => {
  const preset = RESERVATION_PRESETS.find((p) => p === params.get('preset'));
  const sortField = SORT_FIELDS.find((f) => f === params.get('sort'));
  const dir = params.get('dir');
  return {
    preset: preset ?? nav?.preset ?? 'all',
    sortField: sortField ?? nav?.sortField ?? DEFAULT_SORT_FIELD,
    sortDir: dir === 'asc' || dir === 'desc' ? dir : (nav?.sortDir ?? DEFAULT_SORT_DIR),
  };
};

/** Query string for a filter combination; the search text is left out on purpose (personal data). */
export const reservationFiltersToParams = (filters: ReservationListFilters): URLSearchParams =>
  new URLSearchParams({ preset: filters.preset, sort: filters.sortField, dir: filters.sortDir });
