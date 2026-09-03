import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fbService } from '../../services';
import { queryKeys } from '../../lib';

export function useOrders() {
  return useQuery({
    queryKey: queryKeys.fbOrders.all,
    queryFn: () => fbService.getAllOrders(),
  });
}

/** RECEPTIONIST never sees the menu-management card, so `enabled` skips the
 * request entirely instead of fetching and discarding it — matching the
 * original `if (!isAdminOrOwner) return;` guard. A failure here was always
 * silently ignored (the admin menu card just shows nothing) rather than
 * surfaced as a page-level error, since it's a secondary panel, not the
 * page's main content. */
export function useMenuItems(enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.menuItems.all,
    queryFn: () => fbService.getMenuItems(),
    enabled,
  });
}

export function useConfirmOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => fbService.confirmOrder(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.fbOrders.all });
    },
  });
}

export function useDeleteMenuItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => fbService.deleteMenuItem(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.menuItems.all });
    },
  });
}
