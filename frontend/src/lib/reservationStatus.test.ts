import { describe, it, expect } from 'vitest';
import { canMoveReservation, isReservationEditable } from './reservationStatus';

describe('isReservationEditable', () => {
  it.each(['PENDING', 'CONFIRMED', 'PARTIALLY_CHECKED_IN', 'CHECKED_IN'])('allows editing a %s reservation', (status) => {
    expect(isReservationEditable(status)).toBe(true);
  });

  it.each(['CHECKED_OUT', 'CANCELLED', 'NO_SHOW'])('keeps a %s reservation read-only', (status) => {
    expect(isReservationEditable(status)).toBe(false);
  });
});

describe('canMoveReservation', () => {
  it.each(['PENDING', 'CONFIRMED'])('allows moving a %s reservation', (status) => {
    expect(canMoveReservation(status)).toBe(true);
  });

  it.each(['PARTIALLY_CHECKED_IN', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED', 'NO_SHOW'])('blocks moving a %s reservation', (status) => {
    expect(canMoveReservation(status)).toBe(false);
  });
});
