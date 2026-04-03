import { useState, useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { inventoryService } from '../../services/inventoryService';
import type { RoomRequest, RoomResponse, RoomTypeResponse } from '../../types/inventory.types';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3/M3Button';
import { useToastStore } from '../../store/toastStore';
import * as FocusTrapModule from 'focus-trap-react';

const FocusTrap = FocusTrapModule.default ?? FocusTrapModule;

interface Props {
  room?: RoomResponse;
  roomTypes: RoomTypeResponse[];
  onClose: () => void;
  onSaved: () => void;
}

export const RoomFormModal = memo(({ room, roomTypes, onClose, onSaved }: Props) => {
  const { t } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState<RoomRequest>({
    roomNumber: room?.roomNumber || '',
    roomTypeId: room?.roomType.id || (roomTypes.length > 0 ? roomTypes[0].id : ''),
    status: room?.status || 'CLEAN',
    hotelId: room?.hotelId || '',
  });

  const handleChange = useCallback((e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  }, []);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      if (room) {
        await inventoryService.updateRoom(room.id, formData);
        addToast(t('room_updated', { status: t('save') }), 'success');
      } else {
        await inventoryService.createRoom(formData);
        addToast(t('saving'), 'success');
      }
      onSaved();
    } catch (err: unknown) {
      const e = err as {response?: {data?: {detail?: string}}};
      const errorMsg = e.response?.data?.detail || t('failed_update_room');
      addToast(errorMsg, 'error');
    } finally {
      setLoading(false);
    }
  }, [formData, room, onSaved, addToast, t]);

  const inputClass = "block w-full rounded-shape-xs border border-outline px-3 py-2 text-sm font-body bg-transparent text-on-surface focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none";

  return (
    <FocusTrap>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-0" role="dialog" aria-modal="true">
        <div className="fixed inset-0 bg-scrim/40 transition-opacity" onClick={onClose} aria-hidden="true" />
        <div className="relative bg-surface rounded-shape-lg shadow-elevation-3 w-full max-w-md max-h-[90vh] flex flex-col animate-scale-in">
          
          <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant/30">
            <h3 className="text-xl font-display font-medium text-on-surface">
              {room ? t('edit_room') : t('add_room')}
            </h3>
            <button
              onClick={onClose}
              className="w-8 h-8 flex items-center justify-center rounded-shape-full text-on-surface-variant hover:bg-surface-container-highest transition-colors"
            >
              <MaterialIcon name="close" size={20} />
            </button>
          </div>

          <div className="p-6 overflow-y-auto">
            <form id="room-form" onSubmit={handleSubmit} className="space-y-4">
              
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                    {t('room_number')} *
                  </label>
                  <input
                    required
                    type="text"
                    name="roomNumber"
                    value={formData.roomNumber}
                    onChange={handleChange}
                    className={inputClass}
                    placeholder="101"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                    {t('status')} *
                  </label>
                  <select
                    name="status"
                    value={formData.status}
                    onChange={handleChange}
                    className={inputClass}
                  >
                    <option value="CLEAN">{t('room_status_clean')}</option>
                    <option value="DIRTY">{t('room_status_dirty')}</option>
                    <option value="MAINTENANCE">{t('room_status_maintenance')}</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                  {t('room_type')} *
                </label>
                <select
                  required
                  name="roomTypeId"
                  value={formData.roomTypeId}
                  onChange={handleChange}
                  className={inputClass}
                >
                  <option value="" disabled>-- Seleziona --</option>
                  {roomTypes.map(rt => (
                    <option key={rt.id} value={rt.id}>
                      {rt.name} (Max {rt.maxOccupancy} pax)
                    </option>
                  ))}
                </select>
              </div>

            </form>
          </div>

          <div className="flex justify-end gap-2 px-6 py-4 border-t border-outline-variant/30 bg-surface-container-lowest rounded-b-shape-lg">
            <M3Button variant="text" onClick={onClose} disabled={loading}>{t('cancel')}</M3Button>
            <M3Button form="room-form" type="submit" loading={loading} disabled={loading}>{t('save')}</M3Button>
          </div>
        </div>
      </div>
    </FocusTrap>
  );
});

RoomFormModal.displayName = 'RoomFormModal';
