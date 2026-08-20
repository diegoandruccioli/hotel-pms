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
 * availability search — same two backend calls RoomList.tsx already made. */
export function useRoomsList(availableOnly: boolean) {
  return useQuery({
    queryKey: queryKeys.rooms.list(availableOnly),
    queryFn: () =>
      availableOnly
        ? inventoryService.getAvailableRooms(getTodayString(), getTomorrowString())
        : inventoryService.getAllRooms().then((page) => page.content),
  });
}

export function useRoomTypes() {
  return useQuery({
    queryKey: queryKeys.roomTypes.all,
    queryFn: () => inventoryService.getAllRoomTypes(),
  });
}

/**
 * Patches the cached unfiltered room list in place on success instead of
 * invalidating — Housekeeping.tsx did the equivalent with a local
 * `setRooms((prev) => prev.map(...))` before this migration, and a full
 * refetch after every single status click would be a worse experience for
 * what's meant to be a fast, frequent action.
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
    },
  });
}
