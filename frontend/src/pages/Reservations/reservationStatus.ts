import type { TFunction } from 'i18next';

/** Closed reservations (checked out, cancelled, no-show) are history: view only. */
const EDITABLE_STATUSES = new Set(['PENDING', 'CONFIRMED', 'CHECKED_IN']);

export const isReservationEditable = (status: string): boolean => EDITABLE_STATUSES.has(status);

export const getStatusLabel = (status: string, t: TFunction) =>
  t(`status_${status.toLowerCase()}`, status);
