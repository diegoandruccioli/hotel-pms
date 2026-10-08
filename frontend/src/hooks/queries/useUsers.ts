import { useCallback } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { userService } from '../../services';
import type { UserResponse } from '../../types';
import { queryKeys } from '../../lib';

/** Hotel staff accounts — small, unpaginated, admin-only list. */
export function useUsersList() {
  return useQuery({
    queryKey: queryKeys.users.all,
    queryFn: () => userService.listUsers(),
  });
}

/**
 * Toggles `active` on one account and patches the cached list in place — the
 * server returns the updated row, so a refetch of the whole list would only
 * add a round-trip.
 */
export function useToggleUserActive() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (user: UserResponse) =>
      user.active ? userService.deactivateUser(user.id) : userService.activateUser(user.id),
    onSuccess: (updated) => {
      queryClient.setQueryData<UserResponse[]>(queryKeys.users.all, (prev) =>
        prev?.map((x) => (x.id === updated.id ? updated : x)),
      );
    },
  });
}

/** Prepends an account the create modal just saved to the cached list. */
export function useAddUserToCache() {
  const queryClient = useQueryClient();
  return useCallback(
    (user: UserResponse) => {
      queryClient.setQueryData<UserResponse[]>(queryKeys.users.all, (prev) => [user, ...(prev ?? [])]);
    },
    [queryClient],
  );
}
