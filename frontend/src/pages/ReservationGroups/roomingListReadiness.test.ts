import { describe, expect, it } from 'vitest';
import { isMemberReady, summarizeRoomingList } from './roomingListReadiness';
import type { GroupMemberResponse } from '../../types';

const member = (overrides: Partial<GroupMemberResponse> = {}): GroupMemberResponse => ({
  reservationId: 'r1', guestId: 'g1', guestFullName: 'Jane Doe', roomId: 'room1',
  expectedGuests: 1, actualGuests: 0, checkInDate: '2026-10-01', checkOutDate: '2026-10-03',
  status: 'CONFIRMED', billedToMasterFolio: false, price: 100,
  ...overrides,
});

describe('isMemberReady', () => {
  it.each(['CONFIRMED', 'PARTIALLY_CHECKED_IN', 'CHECKED_IN', 'CHECKED_OUT'])('is ready when %s with a room', (status) => {
    expect(isMemberReady(member({ status }))).toBe(true);
  });

  it.each(['PENDING', 'NO_SHOW', 'CANCELLED'])('is not ready when %s', (status) => {
    expect(isMemberReady(member({ status }))).toBe(false);
  });

  it('is not ready without a room, whatever the status', () => {
    expect(isMemberReady(member({ roomId: null }))).toBe(false);
  });
});

describe('summarizeRoomingList', () => {
  it('counts ready members over the non-cancelled ones', () => {
    const summary = summarizeRoomingList([
      member({ reservationId: 'a' }),
      member({ reservationId: 'b', status: 'PENDING' }),
      member({ reservationId: 'c', roomId: null }),
      member({ reservationId: 'd', status: 'CANCELLED' }),
    ]);
    expect(summary).toEqual({ ready: 1, total: 3 });
  });

  it('is empty for an empty list or only cancelled members', () => {
    expect(summarizeRoomingList([])).toEqual({ ready: 0, total: 0 });
    expect(summarizeRoomingList([member({ status: 'CANCELLED' })])).toEqual({ ready: 0, total: 0 });
  });
});
