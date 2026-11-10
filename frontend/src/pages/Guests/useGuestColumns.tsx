import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import type { ColumnDef } from '@tanstack/react-table';
import type { GuestResponseDTO } from '../../types';
import { GuestActionsCell, GuestNameCell } from './GuestCells';

interface GuestColumnsOptions {
  isAdminOrOwner: boolean;
  onOpen: (g: GuestResponseDTO, trigger: HTMLElement) => void;
  onEdit: (g: GuestResponseDTO) => void;
  onDelete: (g: GuestResponseDTO) => void;
  onExport: (g: GuestResponseDTO) => void;
}

/** "Rome (IT)", "Rome", "IT" or a dash: never "- (-)" for a guest with no address on file. */
const formatPlace = ({ city, country }: GuestResponseDTO) => {
  if (city && country) return `${city} (${country})`;
  return city || country || '-';
};

export function useGuestColumns({ isAdminOrOwner, onOpen, onEdit, onDelete, onExport }: GuestColumnsOptions) {
  const { t } = useTranslation('common');

  return useMemo<ColumnDef<GuestResponseDTO>[]>(() => [
    {
      id: 'lastName',
      accessorKey: 'lastName',
      header: t('name'),
      cell: ({ row }) => <GuestNameCell guest={row.original} onOpen={onOpen} />,
    },
    {
      id: 'phone',
      header: t('phone'),
      enableSorting: false,
      cell: ({ row }) => <span className="text-on-surface-variant">{row.original.phone || '-'}</span>,
    },
    {
      id: 'city',
      header: t('city'),
      enableSorting: false,
      cell: ({ row }) => <span className="text-on-surface-variant">{formatPlace(row.original)}</span>,
    },
    {
      id: 'actions',
      header: () => <span className="sr-only">{t('actions')}</span>,
      enableSorting: false,
      cell: ({ row }) => (
        <GuestActionsCell
          guest={row.original}
          onEdit={onEdit}
          onDelete={isAdminOrOwner ? onDelete : undefined}
          onExport={isAdminOrOwner ? onExport : undefined}
          t={t}
        />
      ),
    },
  ], [t, onOpen, onEdit, isAdminOrOwner, onDelete, onExport]);
}
