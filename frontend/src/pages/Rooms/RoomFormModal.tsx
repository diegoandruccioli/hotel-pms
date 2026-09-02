import { useState, useCallback, useMemo, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { inventoryService } from '../../services/inventoryService';
import type { RoomRequest, RoomResponse, RoomTypeResponse } from '../../types';
import { M3Button } from '../../components/m3/M3Button';
import { M3Dialog } from '../../components/m3/M3Dialog';
import { M3Select } from '../../components/m3/M3Select';
import { M3TextField } from '../../components/m3/M3TextField';
import { useToastStore } from '../../store/toastStore';

interface Props {
  room?: RoomResponse;
  roomTypes: RoomTypeResponse[];
  onClose: () => void;
  onSaved: () => void;
}

export const RoomFormModal = memo(({ room, roomTypes, onClose, onSaved }: Props) => {
  const { t } = useTranslation(['rooms', 'common']);
  const addToast = useToastStore((s) => s.addToast);
  const [loading, setLoading] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [formData, setFormData] = useState<RoomRequest>({
    roomNumber: room?.roomNumber || '',
    roomTypeId: room?.roomType.id || (roomTypes.length > 0 ? roomTypes[0].id : ''),
    status: room?.status || 'CLEAN',
    hotelId: room?.hotelId || '',
  });
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const roomSchema = useMemo(() => z.object({
    roomNumber: z.string().trim()
      .min(1, t('common:err_required'))
      .max(10, t('common:err_max_length', { count: 10 })),
    roomTypeId: z.string().trim().min(1, t('common:err_required')),
  }), [t]);

  const handleChange = useCallback((e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  }, []);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});

    const result = roomSchema.safeParse({ roomNumber: formData.roomNumber, roomTypeId: formData.roomTypeId });
    if (!result.success) {
      const errors: Record<string, string> = {};
      for (const issue of result.error.issues) {
        const field = issue.path[0];
        if (typeof field === 'string' && !errors[field]) errors[field] = issue.message;
      }
      setFieldErrors(errors);
      return;
    }

    setLoading(true);
    const submitData = { ...formData, roomNumber: result.data.roomNumber, roomTypeId: result.data.roomTypeId };
    try {
      if (room) {
        await inventoryService.updateRoom(room.id, submitData);
        addToast(t('room_updated', { status: t('save') }), 'success');
      } else {
        await inventoryService.createRoom(submitData);
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
  }, [formData, room, roomSchema, onSaved, addToast, t]);

  const handleDelete = useCallback(async () => {
    if (!room) return;
    setLoading(true);
    try {
      await inventoryService.deleteRoom(room.id);
      addToast(t('toast_deleted'), 'success');
      onSaved();
    } catch (err: unknown) {
      const e = err as {response?: {data?: {detail?: string}}};
      const errorMsg = e.response?.data?.detail || t('toast_delete_error');
      addToast(errorMsg, 'error');
    } finally {
      setLoading(false);
      setShowDeleteConfirm(false);
    }
  }, [room, onSaved, addToast, t]);

  const openDeleteConfirm = useCallback(() => setShowDeleteConfirm(true), []);
  const closeDeleteConfirm = useCallback(() => setShowDeleteConfirm(false), []);

  const statusOptions = useMemo(() => [
    { value: 'CLEAN', label: t('room_status_clean') },
    { value: 'DIRTY', label: t('room_status_dirty') },
    { value: 'MAINTENANCE', label: t('room_status_maintenance') },
  ], [t]);

  const roomTypeOptions = useMemo(() => roomTypes.map((rt) => ({
    value: rt.id,
    label: `${rt.name} (Max ${rt.maxOccupancy} pax)`,
  })), [roomTypes]);

  const footer = showDeleteConfirm ? (
    <div className="flex flex-col sm:flex-row justify-between items-center gap-3">
      <span className="text-sm font-medium font-body text-error">
        {t('confirm_delete_room')}
      </span>
      <div className="flex gap-2">
        <M3Button variant="text" onClick={closeDeleteConfirm} disabled={loading}>{t('cancel')}</M3Button>
        <M3Button onClick={handleDelete} loading={loading} disabled={loading} className="bg-error text-on-error hover:bg-error/90 border-transparent">{t('btn_confirm')}</M3Button>
      </div>
    </div>
  ) : (
    <div className="flex justify-between items-center">
      <div>
        {room && (
          <M3Button variant="text" onClick={openDeleteConfirm} disabled={loading} className="text-error hover:bg-error-container/20">
            {t('delete')}
          </M3Button>
        )}
      </div>
      <div className="flex gap-2">
        <M3Button variant="text" onClick={onClose} disabled={loading}>{t('cancel')}</M3Button>
        <M3Button form="room-form" type="submit" loading={loading} disabled={loading}>{t('save')}</M3Button>
      </div>
    </div>
  );

  return (
    <M3Dialog
      open
      title={room ? t('edit_room') : t('add_room')}
      titleId="room-modal-title"
      onClose={onClose}
      footer={footer}
    >
      <form id="room-form" onSubmit={handleSubmit} noValidate className="space-y-4">

        <div className="grid grid-cols-2 gap-4">
          <M3TextField
            label={`${t('room_number_col')} *`}
            required
            name="roomNumber"
            value={formData.roomNumber}
            onChange={handleChange}
            placeholder="101"
            errorText={fieldErrors.roomNumber}
          />
          <M3Select
            label={t('status')}
            required
            name="status"
            value={formData.status}
            onChange={handleChange}
            options={statusOptions}
          />
        </div>

        <M3Select
          label={t('room_type')}
          required
          name="roomTypeId"
          value={formData.roomTypeId}
          onChange={handleChange}
          options={roomTypeOptions}
          placeholder={t('select_placeholder')}
          errorText={fieldErrors.roomTypeId}
        />

      </form>
    </M3Dialog>
  );
});

RoomFormModal.displayName = 'RoomFormModal';
