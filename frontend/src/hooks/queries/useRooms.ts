import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { inventoryService } from '../../services/inventoryService';
import type { RoomResponse, RoomStatus } from '../../types/inventory.types';
import { queryKeys } from '../../lib/queryKeys';

const getTodayString = (): string => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

const getTomorrowString = (): string => {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  return `${tomorrow.getFullYear()}-${String(tomorrow.getMonth() + 1).padStart(2, '0')}-${String(tomorrow.getDate()).padStart(2, '0')}`;
};

/** `availableOnly` switches between the full room list and today's
 * availability search — same two backend calls RoomList.tsx already made.
 * `status`, when set, applies the server-side housekeeping-status filter
 * instead of downloading every room and filtering in the browser
 * (Housekeeping.tsx). */
export function useRoomsList(availableOnly: boolean, status?: RoomStatus) {
  return useQuery({
    queryKey: queryKeys.rooms.list(availableOnly, status),
    queryFn: () =>
      availableOnly
        ? inventoryService.getAvailableRooms(getTodayString(), getTomorrowString())
        : inventoryService.getAllRooms(0, 100, status).then((page) => page.content),
  });
}

export function useRoomTypes() {
  return useQuery({
    queryKey: queryKeys.roomTypes.all,
    queryFn: () => inventoryService.getAllRoomTypes(),
  });
}

/**
 * Patches the cached unfiltered room list in place on success — membership
 * of that list never changes on a status update, only the row's own status
 * field, so a full refetch would be a worse experience for what's meant to
 * be a fast, frequent action. Status-filtered lists and the availability
 * list, whose *membership* does change when a room's status crosses the
 * filter boundary, are invalidated instead.
 */
export function useUpdateRoomStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: RoomStatus }) =>
      inventoryService.updateRoomStatus(id, status),
    onSuccess: (updated) => {
      queryClient.setQueryData<RoomResponse[]>(queryKeys.rooms.list(false), (prev) =>
        prev?.map((r) => (r.id === updated.id ? updated : r)),
      );
      queryClient.invalidateQueries({ queryKey: queryKeys.rooms.list(true) });
      invalidateFilteredRoomLists(queryClient);
      invalidateDaySheet(queryClient);
    },
  });
}

/** Bulk counterpart of {@link useUpdateRoomStatus} for Housekeeping's
 * multi-select action — membership changes for several rows at once, so a
 * full invalidate (rather than incremental patching) is simpler and correct. */
export function useBulkUpdateRoomStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ roomIds, status }: { roomIds: string[]; status: RoomStatus }) =>
      inventoryService.updateRoomStatusBulk(roomIds, status),
    onSuccess: () => {
      // Several rooms changed at once — membership of every list variant
      // (unfiltered included) may now be stale, so a full invalidate is
      // simpler and correct here, unlike the single-room path above.
      queryClient.invalidateQueries({ queryKey: queryKeys.rooms.all });
      invalidateDaySheet(queryClient);
    },
  });
}

/**
 * Housekeeping's status-count badges read `useDaySheet()`'s
 * `roomStatusCounts` (Housekeeping.tsx:164-168) instead of counting the room
 * list client-side. Neither the single nor bulk status mutation invalidated
 * that query, so the badges stayed stuck at their value from page load —
 * even after the explicit "Aggiorna" button, which only refetches the room
 * list, not the day-sheet. `queryKeys.dashboard.daySheet` is keyed by
 * today's date string (not known here), so invalidate by predicate on the
 * stable `['dashboard', 'day-sheet', ...]` prefix instead of importing
 * useDashboard's date-formatting helper into this file.
 */
function invalidateDaySheet(queryClient: ReturnType<typeof useQueryClient>): void {
  queryClient.invalidateQueries({
    predicate: (query) => query.queryKey[0] === 'dashboard' && query.queryKey[1] === 'day-sheet',
  });
}

/** Invalidates every cached status-filtered room list — their *membership*
 * changes when a room's status crosses the filter boundary, so (unlike the
 * unfiltered list) they can't be patched in place. */
function invalidateFilteredRoomLists(queryClient: ReturnType<typeof useQueryClient>): void {
  queryClient.invalidateQueries({
    predicate: (query) => {
      const key = query.queryKey;
      return key[0] === 'rooms' && key[1] === 'list' && key[2] === false && key[3] !== undefined;
    },
  });
}
