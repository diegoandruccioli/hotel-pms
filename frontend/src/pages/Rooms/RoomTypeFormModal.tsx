import { useState, useCallback, useMemo, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { inventoryService } from '../../services';
import type { RoomTypeRequest, RoomTypeResponse } from '../../types';
import { M3Button } from '../../components/m3';
import { M3Dialog } from '../../components/m3';
import { M3TextField } from '../../components/m3';
import { M3Textarea } from '../../components/m3';
import { useToastStore } from '../../store';

interface Props {
  roomType?: RoomTypeResponse;
  onClose: () => void;
  onSaved: () => void;
}

export const RoomTypeFormModal = memo(({ roomType, onClose, onSaved }: Props) => {
  const { t } = useTranslation(['rooms', 'common']);
  const addToast = useToastStore((s) => s.addToast);
  const [loading, setLoading] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [formData, setFormData] = useState<RoomTypeRequest>({
    name: roomType?.name || '',
    description: roomType?.description || '',
    maxOccupancy: roomType?.maxOccupancy || 1,
    basePrice: roomType?.basePrice || 50.0,
  });
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const roomTypeSchema = useMemo(() => z.object({
    name: z.string().trim()
      .min(1, t('common:err_required'))
      .max(100, t('common:err_max_length', { count: 100 })),
    maxOccupancy: z.number(t('common:err_invalid_number')).int().min(1, t('common:err_must_be_positive')),
    basePrice: z.number(t('common:err_invalid_number')).positive(t('common:err_must_be_positive')),
  }), [t]);

  const handleChange = useCallback((e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: name === 'maxOccupancy' || name === 'basePrice' ? Number(value) : value
    }));
  }, []);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});

    const result = roomTypeSchema.safeParse({
      name: formData.name,
      maxOccupancy: formData.maxOccupancy,
      basePrice: formData.basePrice,
    });
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
    const submitData = { ...formData, ...result.data };
    try {
      if (roomType) {
        await inventoryService.updateRoomType(roomType.id, submitData);
        addToast(t('room_updated', { status: t('save') }), 'success');
      } else {
        await inventoryService.createRoomType(submitData);
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
  }, [formData, roomType, roomTypeSchema, onSaved, addToast, t]);

  const handleDelete = useCallback(async () => {
    if (!roomType) return;
    setLoading(true);
    try {
      await inventoryService.deleteRoomType(roomType.id);
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
  }, [roomType, onSaved, addToast, t]);

  const openDeleteConfirm = useCallback(() => setShowDeleteConfirm(true), []);
  const closeDeleteConfirm = useCallback(() => setShowDeleteConfirm(false), []);

  const footer = showDeleteConfirm ? (
    <div className="flex flex-col sm:flex-row justify-between items-center gap-3">
      <span className="text-sm font-medium font-body text-error">
        {t('confirm_delete_room_type')}
      </span>
      <div className="flex gap-2">
        <M3Button variant="text" onClick={closeDeleteConfirm} disabled={loading}>{t('cancel')}</M3Button>
        <M3Button onClick={handleDelete} loading={loading} disabled={loading} className="bg-error text-on-error hover:bg-error/90 border-transparent">{t('btn_confirm')}</M3Button>
      </div>
    </div>
  ) : (
    <div className="flex justify-between items-center">
      <div>
        {roomType && (
          <M3Button variant="text" onClick={openDeleteConfirm} disabled={loading} className="text-error hover:bg-error-container/20">
            {t('delete')}
          </M3Button>
        )}
      </div>
      <div className="flex gap-2">
        <M3Button variant="text" onClick={onClose} disabled={loading}>{t('cancel')}</M3Button>
        <M3Button form="room-type-form" type="submit" loading={loading} disabled={loading}>{t('save')}</M3Button>
      </div>
    </div>
  );

  return (
    <M3Dialog
      open
      title={roomType ? t('edit_room_type') : t('add_room_type')}
      titleId="room-type-modal-title"
      onClose={onClose}
      footer={footer}
    >
      <form id="room-type-form" onSubmit={handleSubmit} noValidate className="space-y-4">
        <M3TextField
          label={`${t('name')} *`}
          required
          name="name"
          value={formData.name}
          onChange={handleChange}
          errorText={fieldErrors.name}
        />

        <M3Textarea
          label={t('description')}
          name="description"
          value={formData.description}
          onChange={handleChange}
          rows={3}
        />

        <div className="grid grid-cols-2 gap-4">
          <M3TextField
            label={`${t('max_occupancy')} *`}
            required
            type="number"
            min="1"
            name="maxOccupancy"
            value={formData.maxOccupancy}
            onChange={handleChange}
            errorText={fieldErrors.maxOccupancy}
          />
          <M3TextField
            label={`${t('base_price')} *`}
            required
            type="number"
            min="0.01"
            step="0.01"
            name="basePrice"
            value={formData.basePrice}
            onChange={handleChange}
            errorText={fieldErrors.basePrice}
          />
        </div>
      </form>
    </M3Dialog>
  );
});

RoomTypeFormModal.displayName = 'RoomTypeFormModal';
