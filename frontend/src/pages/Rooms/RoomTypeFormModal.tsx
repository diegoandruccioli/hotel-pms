import { useState, useCallback, useMemo, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
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

  const inputClass = "block w-full rounded-shape-xs border border-outline px-3 py-2 text-sm font-body bg-transparent text-on-surface focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none";

  return (
    <FocusTrap>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-0" role="dialog" aria-modal="true" aria-labelledby="room-type-modal-title">
        <div className="fixed inset-0 bg-scrim/40 transition-opacity" onClick={onClose} aria-hidden="true" />
        <div className="relative bg-surface rounded-shape-lg shadow-elevation-3 w-full max-w-md max-h-[90vh] flex flex-col animate-scale-in">

          <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant/30">
            <h3 id="room-type-modal-title" className="text-xl font-display font-medium text-on-surface">
              {roomType ? t('edit_room_type') : t('add_room_type')}
            </h3>
            <button
              onClick={onClose}
              type="button"
              aria-label={t('close')}
              className="w-10 h-10 flex items-center justify-center rounded-shape-full text-on-surface-variant hover:bg-surface-container-highest transition-colors focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:outline-none"
            >
              <MaterialIcon name="close" size={20} />
            </button>
          </div>

          <div className="p-6 overflow-y-auto">
            <form id="room-type-form" onSubmit={handleSubmit} noValidate className="space-y-4">
              <div>
                <label htmlFor="rtName" className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                  {t('name')} *
                </label>
                <input
                  type="text"
                  id="rtName"
                  name="name"
                  value={formData.name}
                  onChange={handleChange}
                  className={inputClass}
                  aria-invalid={!!fieldErrors.name}
                  aria-describedby={fieldErrors.name ? 'rtName-error' : undefined}
                />
                {fieldErrors.name && (
                  <p id="rtName-error" role="alert" className="mt-1 text-sm font-body text-error">{fieldErrors.name}</p>
                )}
              </div>

              <div>
                <label htmlFor="rtDescription" className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                  {t('description')}
                </label>
                <textarea
                  id="rtDescription"
                  name="description"
                  value={formData.description}
                  onChange={handleChange}
                  rows={3}
                  className={inputClass}
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label htmlFor="rtMaxOccupancy" className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                    {t('max_occupancy')} *
                  </label>
                  <input
                    type="number"
                    min="1"
                    id="rtMaxOccupancy"
                    name="maxOccupancy"
                    value={formData.maxOccupancy}
                    onChange={handleChange}
                    className={inputClass}
                    aria-invalid={!!fieldErrors.maxOccupancy}
                    aria-describedby={fieldErrors.maxOccupancy ? 'rtMaxOccupancy-error' : undefined}
                  />
                  {fieldErrors.maxOccupancy && (
                    <p id="rtMaxOccupancy-error" role="alert" className="mt-1 text-sm font-body text-error">{fieldErrors.maxOccupancy}</p>
                  )}
                </div>
                <div>
                  <label htmlFor="rtBasePrice" className="block text-sm font-medium font-body text-on-surface-variant mb-1">
                    {t('base_price')} *
                  </label>
                  <input
                    type="number"
                    min="0.01"
                    step="0.01"
                    id="rtBasePrice"
                    name="basePrice"
                    value={formData.basePrice}
                    onChange={handleChange}
                    className={inputClass}
                    aria-invalid={!!fieldErrors.basePrice}
                    aria-describedby={fieldErrors.basePrice ? 'rtBasePrice-error' : undefined}
                  />
                  {fieldErrors.basePrice && (
                    <p id="rtBasePrice-error" role="alert" className="mt-1 text-sm font-body text-error">{fieldErrors.basePrice}</p>
                  )}
                </div>
              </div>

            </form>
          </div>

          {showDeleteConfirm ? (
            <div className="flex flex-col sm:flex-row justify-between items-center gap-3 px-6 py-4 border-t border-error/20 bg-error-container/20 rounded-b-shape-lg">
              <span className="text-sm font-medium font-body text-error">
                {t('confirm_delete_room_type')}
              </span>
              <div className="flex gap-2">
                <M3Button variant="text" onClick={closeDeleteConfirm} disabled={loading}>{t('cancel')}</M3Button>
                <M3Button onClick={handleDelete} loading={loading} disabled={loading} className="bg-error text-on-error hover:bg-error/90 border-transparent">{t('btn_confirm')}</M3Button>
              </div>
            </div>
          ) : (
            <div className="flex justify-between items-center px-6 py-4 border-t border-outline-variant/30 bg-surface-container-lowest rounded-b-shape-lg">
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
          )}
        </div>
      </div>
    </FocusTrap>
  );
});

RoomTypeFormModal.displayName = 'RoomTypeFormModal';
