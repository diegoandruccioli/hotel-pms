import { useCallback, useMemo } from 'react';
import type { TFunction } from 'i18next';
import type { UserResponse } from '../../types';
import { M3RowActionsMenu, type RowAction } from '../../components/m3';

interface ActionsCellProps {
  user: UserResponse;
  onToggle: (u: UserResponse) => void;
  onResetPassword: (u: UserResponse) => void;
  currentUsername: string | undefined;
  t: TFunction;
}

export const UserActionsCell = ({ user, onToggle, onResetPassword, currentUsername, t }: ActionsCellProps) => {
  const handleToggle = useCallback(() => onToggle(user), [onToggle, user]);
  const handleReset = useCallback(() => onResetPassword(user), [onResetPassword, user]);

  const items = useMemo<RowAction[]>(() => {
    const list: RowAction[] = [{
      id: 'toggle',
      label: user.active ? t('btn_deactivate') : t('btn_activate'),
      icon: user.active ? 'toggle_off' : 'toggle_on',
      onSelect: handleToggle,
    }];
    if (user.username !== currentUsername) {
      list.push({ id: 'reset', label: t('btn_reset_password'), icon: 'lock_reset', onSelect: handleReset });
    }
    return list;
  }, [user.active, user.username, currentUsername, t, handleToggle, handleReset]);

  return (
    <div className="flex items-center justify-end">
      <M3RowActionsMenu label={t('row_actions_for', { ns: 'common', name: user.username })} items={items} />
    </div>
  );
};
