import type { GroupMemberResponse } from '../../types';

const READY_STATUSES = new Set(['CONFIRMED', 'PARTIALLY_CHECKED_IN', 'CHECKED_IN', 'CHECKED_OUT']);

/** A member is ready once it has a room and a reservation that is confirmed or already past that. */
export const isMemberReady = (member: GroupMemberResponse): boolean =>
  member.roomId !== null && READY_STATUSES.has(member.status);

/** Ready members over the non-cancelled ones: a cancelled room is not part of the rooming list any more. */
export const summarizeRoomingList = (members: GroupMemberResponse[]): { ready: number; total: number } => {
  const active = members.filter((m) => m.status !== 'CANCELLED');
  return { ready: active.filter(isMemberReady).length, total: active.length };
};
