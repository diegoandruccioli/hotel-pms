import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import { guestService } from '../services/guestService';
import type { GuestResponseDTO } from '../types/guest.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3Table, M3TableRow, M3TableCell } from '../components/m3/M3Table';
import { useTranslation } from 'react-i18next';
import { GuestFormModal } from './GuestFormModal';

const GuestRow = memo(({ guest, onEdit, t }: { 
  guest: GuestResponseDTO; 
  onEdit: (g: GuestResponseDTO) => void;
  t: (k: string) => string;
}) => {
  const handleEdit = useCallback(() => {
    onEdit(guest);
  }, [onEdit, guest]);

  return (
    <M3TableRow key={guest.id}>
      <M3TableCell className="font-medium">{guest.firstName} {guest.lastName}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{guest.email}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{guest.phone || '-'}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{guest.city || '-'} ({guest.country || '-'})</M3TableCell>
      <M3TableCell className="text-right">
        <button 
          className="text-primary hover:text-primary/80 font-medium text-sm"
          onClick={handleEdit}
        >
          {t('edit')}
        </button>
      </M3TableCell>
    </M3TableRow>
  );
});

export const Guests = memo(() => {
  const { t } = useTranslation('common');
  const [guests, setGuests] = useState<GuestResponseDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedGuest, setSelectedGuest] = useState<GuestResponseDTO | undefined>();

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
              <GuestRow key={guest.id} guest={guest} onEdit={handleOpenEditModal} t={t} />
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
    </div>
  );
});

Guests.displayName = 'Guests';
