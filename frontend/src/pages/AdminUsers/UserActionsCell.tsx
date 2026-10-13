import { useCallback } from 'react';
import type { TFunction } from 'i18next';
import type { UserResponse } from '../../types';
import { M3Button } from '../../components/m3';

interface ActionsCellProps {
  user: UserResponse;
  onToggle: (u: UserResponse) => void;
  onResetPassword: (u: UserResponse) => void;
  currentUsername: string | undefined;
  t: TFunction;
}

const ACTION_BTN_CLASS = 'h-auto min-h-10 px-3 py-1 text-xs';

export const UserActionsCell = ({ user, onToggle, onResetPassword, currentUsername, t }: ActionsCellProps) => {
  const handleToggle = useCallback(() => onToggle(user), [onToggle, user]);
  const handleReset = useCallback(() => onResetPassword(user), [onResetPassword, user]);

  return (
    <div className="flex items-center gap-2">
      <M3Button type="button" variant="outlined" onClick={handleToggle}
        className={ACTION_BTN_CLASS}
        aria-label={user.active ? t('btn_deactivate') : t('btn_activate')}>
        {user.active ? t('btn_deactivate') : t('btn_activate')}
      </M3Button>
      {user.username !== currentUsername && (
        <M3Button type="button" variant="outlined" onClick={handleReset}
          className={ACTION_BTN_CLASS}
          aria-label={`${t('btn_reset_password')} ${user.username}`}>
          {t('btn_reset_password')}
        </M3Button>
      )}
    </div>
  );
};
