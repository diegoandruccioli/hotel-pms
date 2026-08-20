import { useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import type { UserResponse } from '../../types/user.types';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3TableRow, M3TableCell } from '../../components/m3/M3Table';

interface UserRowProps {
  user: UserResponse;
  onToggle: (u: UserResponse) => void;
  onResetPassword: (u: UserResponse) => void;
  currentUsername: string | undefined;
}

export const UserRow = memo(({ user, onToggle, onResetPassword, currentUsername }: UserRowProps) => {
  const { t } = useTranslation('admin');
  const handleToggle = useCallback(() => onToggle(user), [onToggle, user]);
  const handleReset = useCallback(() => onResetPassword(user), [onResetPassword, user]);

  return (
    <M3TableRow>
      <M3TableCell className="font-medium">{user.username}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{user.email}</M3TableCell>
      <M3TableCell>
        <span className="rounded-full bg-secondary-container text-on-secondary-container px-2 py-0.5 text-xs font-medium">
          {user.role}
        </span>
      </M3TableCell>
      <M3TableCell>
        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${
          user.active ? 'bg-tertiary-container text-on-tertiary-container' : 'bg-error-container text-on-error-container'
        }`}>
          {user.active ? t('status_active') : t('status_inactive')}
        </span>
      </M3TableCell>
      <M3TableCell>
        {user.mustChangePassword && (
          <span className="text-xs flex items-center gap-1 text-on-surface-variant">
            <MaterialIcon name="warning" size={14} />
            {t('must_change_pw')}
          </span>
        )}
      </M3TableCell>
      <M3TableCell>
        <div className="flex items-center gap-2">
          <button type="button" onClick={handleToggle}
            className="inline-flex items-center justify-center min-h-[40px] text-xs rounded-full border border-outline px-3 py-1 hover:bg-surface-variant focus:outline-none focus:ring-2 focus:ring-primary"
            aria-label={user.active ? t('btn_deactivate') : t('btn_activate')}>
            {user.active ? t('btn_deactivate') : t('btn_activate')}
          </button>
          {user.username !== currentUsername && (
            <button type="button" onClick={handleReset}
              className="inline-flex items-center justify-center min-h-[40px] text-xs rounded-full border border-outline px-3 py-1 hover:bg-surface-variant focus:outline-none focus:ring-2 focus:ring-primary"
              aria-label={`${t('btn_reset_password')} ${user.username}`}>
              {t('btn_reset_password')}
            </button>
          )}
        </div>
      </M3TableCell>
    </M3TableRow>
  );
});
UserRow.displayName = 'UserRow';
