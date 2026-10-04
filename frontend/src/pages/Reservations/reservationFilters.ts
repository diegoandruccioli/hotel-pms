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
