import { useState, useCallback, useMemo, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { rateSeasonService } from '../../services/rateSeasonService';
import type { RateBulkApplyRequest } from '../../types/inventory.types';
import { M3Button } from '../../components/m3/M3Button';
import { M3Dialog } from '../../components/m3/M3Dialog';
import { M3TextField } from '../../components/m3/M3TextField';
import { useToastStore } from '../../store/toastStore';
import { getErrorMessage } from '../../utils/errorMessage';

interface RoomTypeOption {
  id: string;
  name: string;
}

interface Props {
  roomTypes: RoomTypeOption[];
  initialRoomTypeIds?: string[];
  initialStartDate?: string;
  initialEndDate?: string;
  onClose: () => void;
  onApplied: () => void;
}

const RoomTypeCheckboxRow = memo(({ roomType, checked, onToggle }: {
  roomType: RoomTypeOption;
  checked: boolean;
  onToggle: (id: string) => void;
}) => {
  const handleChange = useCallback(() => onToggle(roomType.id), [onToggle, roomType.id]);
  return (
    <label className="flex items-center gap-2 py-1.5 px-2 rounded-shape-xs hover:bg-surface-container-low cursor-pointer">
      <input type="checkbox" checked={checked} onChange={handleChange} className="w-4 h-4" />
      <span className="text-sm font-body text-on-surface">{roomType.name}</span>
    </label>
  );
});
RoomTypeCheckboxRow.displayName = 'RoomTypeCheckboxRow';

export const RateBulkApplyDialog = memo(({
  roomTypes, initialRoomTypeIds, initialStartDate, initialEndDate, onClose, onApplied,
}: Props) => {
  const { t } = useTranslation(['common']);
  const addToast = useToastStore((s) => s.addToast);

  const [selectedIds, setSelectedIds] = useState<string[]>(initialRoomTypeIds ?? []);
  const [startDate, setStartDate] = useState(initialStartDate ?? '');
  const [endDate, setEndDate] = useState(initialEndDate ?? '');
  const [nightlyPrice, setNightlyPrice] = useState<number | ''>('');
  const [name, setName] = useState('');
  const [saving, setSaving] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const schema = useMemo(() => z.object({
    roomTypeIds: z.array(z.string()).min(1, t('err_select_room_type')),
    startDate: z.string().min(1, t('err_required')),
    endDate: z.string().min(1, t('err_required')),
    nightlyPrice: z.number(t('err_invalid_number')).positive(t('err_must_be_positive')),
    name: z.string().trim().max(100, t('err_max_length', { count: 100 })).optional(),
  }).refine((data) => data.endDate >= data.startDate, {
    message: t('err_invalid_date_range'),
    path: ['endDate'],
  }), [t]);

  const toggleRoomType = useCallback((id: string) => {
    setSelectedIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  }, []);

  const handleStartDateChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setStartDate(e.target.value), []);
  const handleEndDateChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setEndDate(e.target.value), []);
  const handlePriceChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setNightlyPrice(value === '' ? '' : Number(value));
  }, []);
  const handleNameChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setName(e.target.value), []);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});

    const result = schema.safeParse({
      roomTypeIds: selectedIds,
      startDate,
      endDate,
      nightlyPrice: nightlyPrice === '' ? Number.NaN : nightlyPrice,
      name: name.trim() || undefined,
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

    setSaving(true);
    try {
      const payload: RateBulkApplyRequest = {
        roomTypeIds: result.data.roomTypeIds,
        startDate: result.data.startDate,
        endDate: result.data.endDate,
        nightlyPrice: result.data.nightlyPrice,
        name: result.data.name,
      };
      await rateSeasonService.bulkApplyRate(payload);
      addToast(t('toast_bulk_apply_success'), 'success');
      onApplied();
      onClose();
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } }).response?.status;
      const message = status === 409
        ? t('err_rate_season_overlap')
        : getErrorMessage(err, t('toast_bulk_apply_error'));
      addToast(message, 'error');
    } finally {
      setSaving(false);
    }
  }, [schema, selectedIds, startDate, endDate, nightlyPrice, name, addToast, t, onApplied, onClose]);

  const footer = (
    <div className="flex justify-end gap-2">
      <M3Button variant="text" onClick={onClose} disabled={saving}>{t('cancel')}</M3Button>
      <M3Button form="rate-bulk-apply-form" type="submit" loading={saving} disabled={saving}>{t('save')}</M3Button>
    </div>
  );

  return (
    <M3Dialog
      open
      title={t('apply_price_dialog_title')}
      titleId="rate-bulk-apply-title"
      onClose={onClose}
      footer={footer}
    >
      <form id="rate-bulk-apply-form" onSubmit={handleSubmit} noValidate className="space-y-4">
        <fieldset>
          <legend className="block text-sm font-medium font-body text-on-surface-variant mb-1">
            {t('label_room_types')}
          </legend>
          <div className="border border-outline-variant rounded-shape-xs max-h-40 overflow-y-auto p-1">
            {roomTypes.map((rt) => (
              <RoomTypeCheckboxRow key={rt.id} roomType={rt} checked={selectedIds.includes(rt.id)} onToggle={toggleRoomType} />
            ))}
          </div>
          {fieldErrors.roomTypeIds && (
            <p role="alert" className="mt-1 text-sm font-body text-error">{fieldErrors.roomTypeIds}</p>
          )}
        </fieldset>

        <div className="grid grid-cols-2 gap-4">
          <M3TextField
            label={t('rate_season_start_date')}
            type="date"
            value={startDate}
            onChange={handleStartDateChange}
            errorText={fieldErrors.startDate}
            required
          />
          <M3TextField
            label={t('rate_season_end_date')}
            type="date"
            value={endDate}
            onChange={handleEndDateChange}
            errorText={fieldErrors.endDate}
            required
          />
        </div>

        <M3TextField
          label={t('rate_season_nightly_price')}
          type="number"
          min="0.01"
          step="0.01"
          value={nightlyPrice}
          onChange={handlePriceChange}
          errorText={fieldErrors.nightlyPrice}
          required
        />

        <M3TextField
          label={t('rate_season_name')}
          value={name}
          onChange={handleNameChange}
          placeholder={t('rate_season_name_placeholder')}
          errorText={fieldErrors.name}
        />
      </form>
    </M3Dialog>
  );
});

RateBulkApplyDialog.displayName = 'RateBulkApplyDialog';
