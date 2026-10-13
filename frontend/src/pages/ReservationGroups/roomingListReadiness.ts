import type { GroupMemberResponse } from '../../types';

const READY_STATUSES = new Set(['CONFIRMED', 'PARTIALLY_CHECKED_IN', 'CHECKED_IN', 'CHECKED_OUT']);

/** A member is ready once it has a room and a reservation that is confirmed or already past that. */
export const isMemberReady = (member: GroupMemberResponse): boolean =>
  member.roomId !== null && READY_STATUSES.has(member.status);

/** Ready members over the ones still expected: a cancelled or no-show room can never become ready. */
export const summarizeRoomingList = (members: GroupMemberResponse[]): { ready: number; total: number } => {
  const active = members.filter((m) => m.status !== 'CANCELLED' && m.status !== 'NO_SHOW');
  return { ready: active.filter(isMemberReady).length, total: active.length };
};
