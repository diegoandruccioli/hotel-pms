import { useState, useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { inventoryService } from '../../services/inventoryService';
import type { RoomTypeRequest, RoomTypeResponse } from '../../types/inventory.types';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3/M3Button';
import { useToastStore } from '../../store/toastStore';
import * as FocusTrapModule from 'focus-trap-react';

const FocusTrap = FocusTrapModule.default ?? FocusTrapModule;

interface Props {
  roomType?: RoomTypeResponse;
  onClose: () => void;
  onSaved: () => void;
}

export const RoomTypeFormModal = memo(({ roomType, onClose, onSaved }: Props) => {
  const { t } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);
  const [loading, setLoading] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [formData, setFormData] = useState<RoomTypeRequest>({
    name: roomType?.name || '',
    description: roomType?.description || '',
    maxOccupancy: roomType?.maxOccupancy || 1,
    basePrice: roomType?.basePrice || 50.0,
  });

  const handleChange = useCallback((e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ 
      ...prev, 
      [name]: name === 'maxOccupancy' || name === 'basePrice' ? Number(value) : value 
    }));
  }, []);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      if (roomType) {
        await inventoryService.updateRoomType(roomType.id, formData);
        addToast(t('room_updated', { status: t('save') }), 'success');
      } else {
        await inventoryService.createRoomType(formData);
        addToast(t('saving'), 'success'); // generic success
      }
      onSaved();
    } catch (err: unknown) {
      const e = err as {response?: {data?: {detail?: string}}};
      const errorMsg = e.response?.data?.detail || t('failed_update_room');
      addToast(errorMsg, 'error');
    } finally {
      setLoading(false);
    }
  }, [formData, roomType, onSaved, addToast, t]);

  const handleDelete = useCallback(async () => {
    if (!roomType) return;
    setLoading(true);
    try {
      await inventoryService.deleteRoomType(roomType.id);
      addToast(t('item_deleted', 'Eliminato con successo'), 'success');
      onSaved();
    } catch (err: unknown) {
      const e = err as {response?: {data?: {detail?: string}}};
      const errorMsg = e.response?.data?.detail || t('failed_delete_room', 'Errore durante l\'eliminazione');
      addToast(errorMsg, 'error');
    } finally {
      setLoading(false);
      setShowDeleteConfirm(false);
    }
  }, [roomType, onSaved, addToast, t]);

  const openDeleteConfirm = useCallback(() => setShowDeleteConfirm(true), []);
  const closeDeleteConfirm = useCallback(() => setShowDeleteConfirm(false), []);

  const inputClass = "block w-full rounded-shape-xs border border-outline px-3 py-2 text-sm font-body bg-transparent text-on-surface focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none";

  return (
    <FocusTrap>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-0" role="dialog" aria-modal="true">
        <div className="fixed inset-0 bg-scrim/40 transition-opacity" onClick={onClose} aria-hidden="true" />
        <div className="relative bg-surface rounded-shape-lg shadow-elevation-3 w-full max-w-md max-h-[90vh] flex flex-col animate-scale-in">
          
          <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant/30">
            <h3 className="text-xl font-display font-medium text-on-surface">
              {roomType ? t('edit_room_type') : t('add_room_type')}
            </h3>
            <button
              onClick={onClose}
              className="w-8 h-8 flex items-center justify-center rounded-shape-full text-on-surface-variant hover:bg-surface-container-highest transition-colors"
            >
              <MaterialIcon name="close" size={20} />
            </button>
          </div>

          <div className="p-6 overflow-y-auto">
            <form id="room-type-form" onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                  {t('name')} *
                </label>
                <input
                  required
                  type="text"
                  name="name"
                  value={formData.name}
                  onChange={handleChange}
                  className={inputClass}
                />
              </div>

              <div>
                <label className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                  {t('description')}
                </label>
                <textarea
                  name="description"
                  value={formData.description}
                  onChange={handleChange}
                  rows={3}
                  className={inputClass}
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                    {t('max_occupancy')} *
                  </label>
                  <input
                    required
                    type="number"
                    min="1"
                    name="maxOccupancy"
                    value={formData.maxOccupancy}
                    onChange={handleChange}
                    className={inputClass}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                    {t('base_price')} *
                  </label>
                  <input
                    required
                    type="number"
                    min="0.01"
                    step="0.01"
                    name="basePrice"
                    value={formData.basePrice}
                    onChange={handleChange}
                    className={inputClass}
                  />
                </div>
              </div>

            </form>
          </div>

          {showDeleteConfirm ? (
            <div className="flex flex-col sm:flex-row justify-between items-center gap-3 px-6 py-4 border-t border-error/20 bg-error-container/20 rounded-b-shape-lg">
              <span className="text-sm font-medium font-body text-error">
                {t('confirm_delete', 'Sei sicuro di voler eliminare questa tipologia?')}
              </span>
              <div className="flex gap-2">
                <M3Button variant="text" onClick={closeDeleteConfirm} disabled={loading}>{t('cancel')}</M3Button>
                <M3Button onClick={handleDelete} loading={loading} disabled={loading} className="bg-error text-on-error hover:bg-error/90 border-transparent">{t('confirm', 'Conferma')}</M3Button>
              </div>
            </div>
          ) : (
            <div className="flex justify-between items-center px-6 py-4 border-t border-outline-variant/30 bg-surface-container-lowest rounded-b-shape-lg">
              <div>
                {roomType && (
                  <M3Button variant="text" onClick={openDeleteConfirm} disabled={loading} className="text-error hover:bg-error-container/20">
                    {t('delete', 'Elimina')}
                  </M3Button>
                )}
              </div>
              <div className="flex gap-2">
                <M3Button variant="text" onClick={onClose} disabled={loading}>{t('cancel')}</M3Button>
                <M3Button form="room-type-form" type="submit" loading={loading} disabled={loading}>{t('save')}</M3Button>
              </div>
            </div>
          )}
        </div>
      </div>
    </FocusTrap>
  );
});

RoomTypeFormModal.displayName = 'RoomTypeFormModal';
