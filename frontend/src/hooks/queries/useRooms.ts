import { useQuery } from '@tanstack/react-query';
import { inventoryService } from '../../services/inventoryService';
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
