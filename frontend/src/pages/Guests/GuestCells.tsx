import { useCallback } from 'react';
import type { TFunction } from 'i18next';
import type { GuestResponseDTO } from '../../types';
import { M3Avatar, M3TableActionLink } from '../../components/m3';

interface ActionsCellProps {
  guest: GuestResponseDTO;
  onEdit: (g: GuestResponseDTO) => void;
  onDelete?: (g: GuestResponseDTO) => void;
  onExport?: (g: GuestResponseDTO) => void;
  t: TFunction;
}

export const GuestActionsCell = ({ guest, onEdit, onDelete, onExport, t }: ActionsCellProps) => {
  const handleEdit = useCallback(() => onEdit(guest), [onEdit, guest]);
  const handleDeleteClick = useCallback(() => onDelete?.(guest), [onDelete, guest]);
  const handleExportClick = useCallback(() => onExport?.(guest), [onExport, guest]);

  return (
    <div className="text-right">
      <M3TableActionLink onClick={handleEdit}>
        {t('edit')}
      </M3TableActionLink>
      {onExport && (
        <M3TableActionLink
          className="ml-3"
          aria-label={`${t('export_guest_data')} ${guest.firstName} ${guest.lastName}`}
          onClick={handleExportClick}
        >
          {t('export_guest_data')}
        </M3TableActionLink>
      )}
      {onDelete && (
        <M3TableActionLink
          tone="error"
          className="ml-3"
          aria-label={`${t('delete')} ${guest.firstName} ${guest.lastName}`}
          onClick={handleDeleteClick}
        >
          {t('delete')}
        </M3TableActionLink>
      )}
    </div>
  );
};

interface NameCellProps {
  guest: GuestResponseDTO;
  onOpen: (g: GuestResponseDTO, trigger: HTMLElement) => void;
}

/** The name is a button, not a clickable row, so keyboard and screen-reader users reach the detail too. */
export const GuestNameCell = ({ guest, onOpen }: NameCellProps) => {
  const handleOpen = useCallback(
    (e: React.MouseEvent<HTMLButtonElement>) => onOpen(guest, e.currentTarget),
    [onOpen, guest],
  );
  return (
    <button
      type="button"
      onClick={handleOpen}
      aria-haspopup="dialog"
      className="flex items-center gap-3 text-left rounded-shape-sm focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary"
    >
      <M3Avatar name={guest.firstName || guest.lastName} size="md" aria-hidden="true" />
      <span className="min-w-0">
        <span className="block font-medium">{guest.firstName} {guest.lastName}</span>
        {guest.email && <span className="block text-xs text-on-surface-variant">{guest.email}</span>}
      </span>
    </button>
  );
};
