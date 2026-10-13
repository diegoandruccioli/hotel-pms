import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import type { ColumnDef } from '@tanstack/react-table';
import type { ReservationResponse, RoomResponse } from '../../types';
import { useFormatters } from '../../hooks';
import { EMPTY_PLACEHOLDER, nightsBetween } from '../../utils';
import { ActionsCell, GuestNameCell, GuestsCountCell, RoomsCell, StatusCell } from './ReservationRowCells';

interface ReservationColumnsOptions {
  rooms: RoomResponse[];
  onRetryConfirmationEmail: (id: string) => void;
  retryingEmail: string | null;
  onCheckIn: (reservationId: string, roomId: string, expectedGuests: number, guestId: string) => void;
  onView: (reservationId: string) => void;
  onEdit: (reservationId: string) => void;
  onDelete: ((reservationId: string) => void) | undefined;
  onMarkNoShow: (reservationId: string) => void;
}

export function useReservationColumns({
  rooms, onRetryConfirmationEmail, retryingEmail, onCheckIn, onView, onEdit, onDelete, onMarkNoShow,
}: ReservationColumnsOptions) {
  const { t } = useTranslation('common');
  const { formatDate } = useFormatters();

  return useMemo<ColumnDef<ReservationResponse>[]>(() => [
    {
      id: 'guestFullName',
      header: t('guest_name'),
      enableSorting: false,
      cell: ({ row }) => <GuestNameCell name={row.original.guestFullName} />,
    },
    {
      id: 'checkInDate',
      accessorKey: 'checkInDate',
      header: t('check_in'),
      cell: ({ row }) => <span className="text-on-surface-variant">{formatDate(row.original.checkInDate)}</span>,
    },
    {
      id: 'checkOutDate',
      accessorKey: 'checkOutDate',
      header: t('check_out'),
      cell: ({ row }) => <span className="text-on-surface-variant">{formatDate(row.original.checkOutDate)}</span>,
    },
    {
      id: 'nights',
      header: t('nights'),
      enableSorting: false,
      cell: ({ row }) => (
        <span data-testid={`nights-${row.original.id}`} className="text-on-surface-variant tabular-nums">
          {nightsBetween(row.original.checkInDate, row.original.checkOutDate) ?? EMPTY_PLACEHOLDER}
        </span>
      ),
    },
    {
      id: 'rooms',
      header: t('nav_rooms'),
      enableSorting: false,
      cell: ({ row }) => <RoomsCell reservation={row.original} rooms={rooms} />,
    },
    {
      id: 'guests',
      header: t('guests'),
      enableSorting: false,
      cell: ({ row }) => <GuestsCountCell reservation={row.original} />,
    },
    {
      id: 'status',
      accessorKey: 'status',
      header: t('status'),
      cell: ({ row }) => (
        <StatusCell
          reservation={row.original}
          onRetryConfirmationEmail={onRetryConfirmationEmail}
          retryingEmail={retryingEmail}
          t={t}
        />
      ),
    },
    {
      id: 'actions',
      header: () => <span className="sr-only">{t('actions')}</span>,
      enableSorting: false,
      cell: ({ row }) => (
        <ActionsCell
          reservation={row.original}
          onCheckIn={onCheckIn}
          onView={onView}
          onEdit={onEdit}
          onDelete={onDelete}
          onMarkNoShow={onMarkNoShow}
          t={t}
        />
      ),
    },
  ], [t, formatDate, rooms, onRetryConfirmationEmail, retryingEmail, onCheckIn, onView, onEdit, onDelete, onMarkNoShow]);
}
