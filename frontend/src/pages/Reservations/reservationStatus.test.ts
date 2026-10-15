import { describe, it, expect } from 'vitest';
import { isReservationEditable } from './reservationStatus';

describe('isReservationEditable', () => {
  it.each(['PENDING', 'CONFIRMED', 'CHECKED_IN'])('allows editing a %s reservation', (status) => {
    expect(isReservationEditable(status)).toBe(true);
  });

  it.each(['CHECKED_OUT', 'CANCELLED', 'NO_SHOW'])('keeps a %s reservation read-only', (status) => {
    expect(isReservationEditable(status)).toBe(false);
  });
});
