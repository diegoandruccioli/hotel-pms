import type { TFunction } from 'i18next';

/** Closed reservations (checked out, cancelled, no-show) are history: view only. Mirrors the server's
 * terminal statuses, so every other status (including a partly checked-in group room) stays editable. */
const EDITABLE_STATUSES = new Set(['PENDING', 'CONFIRMED', 'PARTIALLY_CHECKED_IN', 'CHECKED_IN']);

export const isReservationEditable = (status: string): boolean => EDITABLE_STATUSES.has(status);

/** A booking can be dragged to another room only before arrival: once guests are in house the room
 * is owned by the stay (moved from Stays), and closed bookings are rejected by the server. */
const MOVABLE_STATUSES = new Set(['PENDING', 'CONFIRMED']);

export const canMoveReservation = (status: string): boolean => MOVABLE_STATUSES.has(status);

export const getStatusLabel = (status: string, t: TFunction) =>
  t(`status_${status.toLowerCase()}`, status);
