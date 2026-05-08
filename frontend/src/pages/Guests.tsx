import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import { guestService } from '../services/guestService';
import type { GuestResponseDTO } from '../types/guest.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3Table, M3TableRow, M3TableCell } from '../components/m3/M3Table';
import { M3Dialog } from '../components/m3/M3Dialog';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../store/authStore';
import { useToastStore } from '../store/toastStore';
import { GuestFormModal } from './GuestFormModal';

interface GuestRowProps {
  guest: GuestResponseDTO;
  onEdit: (g: GuestResponseDTO) => void;
  onDelete?: (g: GuestResponseDTO) => void;
  t: (k: string) => string;
}

const GuestRow = memo(({ guest, onEdit, onDelete, t }: GuestRowProps) => {
  const handleEdit = useCallback(() => onEdit(guest), [onEdit, guest]);
  const handleDeleteClick = useCallback(() => onDelete?.(guest), [onDelete, guest]);

  return (
    <M3TableRow key={guest.id}>
      <M3TableCell className="font-medium">{guest.firstName} {guest.lastName}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{guest.email}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{guest.phone || '-'}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{guest.city || '-'} ({guest.country || '-'})</M3TableCell>
      <M3TableCell className="text-right">
        <button
          type="button"
          className="text-primary hover:text-primary/80 font-medium text-sm rounded focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-1 focus-visible:outline-none"
          onClick={handleEdit}
        >
          {t('edit')}
        </button>
        {onDelete && (
          <button
            type="button"
            aria-label={`${t('delete')} ${guest.firstName} ${guest.lastName}`}
            onClick={handleDeleteClick}
            className="ml-3 text-error hover:text-error/80 font-medium text-sm rounded focus-visible:ring-2 focus-visible:ring-error focus-visible:ring-offset-1 focus-visible:outline-none"
          >
            {t('delete')}
          </button>
        )}
      </M3TableCell>
    </M3TableRow>
  );
});

export const Guests = memo(() => {
  const { t } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);
  const role = useAuthStore((s) => s.user?.role);
  const isAdminOrOwner = role === 'ADMIN' || role === 'OWNER';

  const [guests, setGuests] = useState<GuestResponseDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedGuest, setSelectedGuest] = useState<GuestResponseDTO | undefined>();
  const [guestToDelete, setGuestToDelete] = useState<GuestResponseDTO | null>(null);
  const [deleting, setDeleting] = useState(false);

  const loadGuests = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await guestService.getAllGuests();
      setGuests(data);
    } catch (err: unknown) {
      const e = err as {response?: {data?: {detail?: string}}, message?: string};
      setError(e.response?.data?.detail || e.message || 'Failed to load guests');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadGuests();
  }, [loadGuests]);

  const handleOpenAddModal = useCallback(() => {
    setSelectedGuest(undefined);
    setIsModalOpen(true);
  }, []);

  const handleOpenEditModal = useCallback((guest: GuestResponseDTO) => {
    setSelectedGuest(guest);
    setIsModalOpen(true);
  }, []);

  const handleCloseModal = useCallback(() => {
    setIsModalOpen(false);
  }, []);

  const handleSaved = useCallback(() => {
    setIsModalOpen(false);
    loadGuests();
  }, [loadGuests]);

  const handleDeleteRequest = useCallback((guest: GuestResponseDTO) => {
    setGuestToDelete(guest);
  }, []);

  const handleDeleteCancel = useCallback(() => {
    setGuestToDelete(null);
  }, []);

  const handleDeleteConfirm = useCallback(async () => {
    if (!guestToDelete) return;
    setDeleting(true);
    try {
      await guestService.deleteGuest(guestToDelete.id);
      setGuests((prev) => prev.filter((g) => g.id !== guestToDelete.id));
      addToast(t('guest_deleted_success'), 'success');
    } catch (err: unknown) {
      const e = err as { response?: { status?: number } };
      if (e.response?.status === 451) {
        addToast(t('delete_guest_gdpr_hold'), 'error');
      } else {
        addToast(t('delete_guest_failed'), 'error');
      }
    } finally {
      setDeleting(false);
      setGuestToDelete(null);
    }
  }, [guestToDelete, addToast, t]);

  const headers = useMemo(() => [
    t('name'),
    t('email'),
    t('phone'),
    t('city'),
    <span key="sr" className="sr-only">{t('actions')}</span>
  ], [t]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center">
            <MaterialIcon name="group" className="mr-2 text-primary" />
            {t('nav_guests')}
          </h1>
          <p className="text-sm font-body text-on-surface-variant mt-1">{t('guests_subtitle')}</p>
        </div>
        <M3Button icon="add" onClick={handleOpenAddModal}>
          {t('add_guest')}
        </M3Button>
      </div>

      {loading ? (
        <div className="flex justify-center items-center h-64 bg-surface rounded-shape-md shadow-elevation-1">
          <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
        </div>
      ) : error ? (
        <div className="flex items-center gap-3 px-4 py-4 rounded-shape-sm bg-error-container text-on-error-container">
          <MaterialIcon name="error" size={20} className="flex-shrink-0" />
          <div>
            <h3 className="text-sm font-medium font-body">{t('error_loading_guests')}</h3>
            <p className="mt-1 text-sm font-body opacity-80">{error}</p>
            <button type="button" onClick={loadGuests} className="mt-2 text-sm font-medium underline hover:no-underline">
              {t('try_again')}
            </button>
          </div>
        </div>
      ) : (
        <M3Table headers={headers}>
          {guests.length === 0 ? (
            <tr><td colSpan={5} className="py-8 text-center text-sm font-body text-on-surface-variant">{t('no_guests_found')}</td></tr>
          ) : (
            guests.map((guest) => (
              <GuestRow
                key={guest.id}
                guest={guest}
                onEdit={handleOpenEditModal}
                onDelete={isAdminOrOwner ? handleDeleteRequest : undefined}
                t={t}
              />
            ))
          )}
        </M3Table>
      )}

      {isModalOpen && (
        <GuestFormModal
          guest={selectedGuest}
          onClose={handleCloseModal}
          onSaved={handleSaved}
        />
      )}

      {guestToDelete && (
        <M3Dialog
          open
          title={t('delete')}
          titleId="confirm-delete-guest-dialog"
          onClose={handleDeleteCancel}
        >
          <p className="text-sm font-body text-on-surface">{t('delete_guest_confirm')}</p>
          <div className="flex justify-end gap-3 pt-4">
            <M3Button type="button" variant="outlined" onClick={handleDeleteCancel} disabled={deleting}>
              {t('cancel')}
            </M3Button>
            <M3Button type="button" onClick={handleDeleteConfirm} loading={deleting}>
              {t('delete')}
            </M3Button>
          </div>
        </M3Dialog>
      )}
    </div>
  );
});

Guests.displayName = 'Guests';
