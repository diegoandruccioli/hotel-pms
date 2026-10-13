import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import type { ColumnDef } from '@tanstack/react-table';
import type { UserResponse } from '../../types';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Avatar, M3StatusChip } from '../../components/m3';
import { userRoleTone } from '../../utils';
import { UserActionsCell } from './UserActionsCell';

interface UserColumnsOptions {
  onToggle: (u: UserResponse) => void;
  onResetPassword: (u: UserResponse) => void;
  currentUsername: string | undefined;
}

export function useUserColumns({ onToggle, onResetPassword, currentUsername }: UserColumnsOptions) {
  const { t } = useTranslation('admin');

  return useMemo<ColumnDef<UserResponse>[]>(() => [
    {
      id: 'username',
      accessorKey: 'username',
      header: t('col_username'),
      cell: ({ row }) => (
        <div className="flex items-center gap-3">
          <M3Avatar name={row.original.username} size="md" aria-hidden="true" />
          <span className="font-medium">{row.original.username}</span>
        </div>
      ),
    },
    {
      id: 'email',
      accessorKey: 'email',
      header: t('col_email'),
      cell: ({ row }) => <span className="text-on-surface-variant">{row.original.email}</span>,
    },
    {
      id: 'role',
      accessorKey: 'role',
      header: t('col_role'),
      cell: ({ row }) => (
        <M3StatusChip label={row.original.role} tone={userRoleTone[row.original.role]} />
      ),
    },
    {
      id: 'active',
      accessorKey: 'active',
      header: t('col_status'),
      cell: ({ row }) => (
        <div className="flex flex-col items-start gap-1">
          <M3StatusChip
            label={row.original.active ? t('status_active') : t('status_inactive')}
            tone={row.original.active ? 'success' : 'error'}
          />
          {row.original.mustChangePassword && (
            <span className="flex items-center gap-1 text-xs text-on-surface-variant">
              <MaterialIcon name="warning" size={14} />
              {t('must_change_pw')}
            </span>
          )}
        </div>
      ),
    },
    {
      id: 'actions',
      header: t('col_actions'),
      enableSorting: false,
      cell: ({ row }) => (
        <UserActionsCell
          user={row.original}
          onToggle={onToggle}
          onResetPassword={onResetPassword}
          currentUsername={currentUsername}
          t={t}
        />
      ),
    },
  ], [t, onToggle, onResetPassword, currentUsername]);
}
