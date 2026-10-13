import { useFormatters } from '../../hooks';
import { useState, useCallback, useMemo, useEffect, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { rateSeasonService } from '../../services';
import type { RateSeasonRequest, RateSeasonResponse, RoomTypeResponse } from '../../types';
import { M3LoadingState } from '../../components/m3';
import { M3EmptyState } from '../../components/m3';
import { M3Button } from '../../components/m3';
import { M3ConfirmDialog } from '../../components/m3';
import { M3Dialog } from '../../components/m3';
import { M3Table, M3TableRow, M3TableCell } from '../../components/m3';
import { M3TableActionLink } from '../../components/m3';
import { M3TextField } from '../../components/m3';
import { useToastStore } from '../../store';

interface Props {
  roomType: RoomTypeResponse;
  onClose: () => void;
}

const EMPTY_FORM: RateSeasonRequest = { name: '', startDate: '', endDate: '', nightlyPrice: 0 };

const SeasonRow = memo(({ season, onEdit, onDelete, t }: {
  season: RateSeasonResponse;
  onEdit: (season: RateSeasonResponse) => void;
  onDelete: (season: RateSeasonResponse) => void;
  t: (k: string) => string;
}) => {
  const { formatCurrency, formatDate } = useFormatters();
  const handleEdit = useCallback(() => onEdit(season), [onEdit, season]);
  const handleDelete = useCallback(() => onDelete(season), [onDelete, season]);

  return (
    <M3TableRow>
      <M3TableCell className="font-medium">{season.name || '-'}</M3TableCell>
      <M3TableCell>{formatDate(season.startDate)}</M3TableCell>
      <M3TableCell>{formatDate(season.endDate)}</M3TableCell>
      <M3TableCell>{formatCurrency(season.nightlyPrice)}</M3TableCell>
      <M3TableCell className="text-right space-x-2">
        <M3TableActionLink onClick={handleEdit}>
          {t('edit')}
        </M3TableActionLink>
        <M3TableActionLink onClick={handleDelete} tone="error">
          {t('delete')}
        </M3TableActionLink>
      </M3TableCell>
    </M3TableRow>
  );
});
SeasonRow.displayName = 'SeasonRow';

export const RateSeasonManagerModal = memo(({ roomType, onClose }: Props) => {
  const { t } = useTranslation(['rooms', 'common']);
  const addToast = useToastStore((s) => s.addToast);

  const [seasons, setSeasons] = useState<RateSeasonResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [mode, setMode] = useState<'list' | 'form'>('list');
  const [editingSeason, setEditingSeason] = useState<RateSeasonResponse | undefined>();
  const [deletingSeason, setDeletingSeason] = useState<RateSeasonResponse | undefined>();
  const [formData, setFormData] = useState<RateSeasonRequest>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const loadSeasons = useCallback(async () => {
    try {
      setLoading(true);
      const data = await rateSeasonService.listSeasons(roomType.id);
      setSeasons(data);
    } catch (err: unknown) {
      const e = err as { response?: { data?: { detail?: string } }, message?: string };
      addToast(e.response?.data?.detail || e.message || t('error_loading_rate_seasons'), 'error');
    } finally {
      setLoading(false);
    }
  }, [roomType.id, addToast, t]);

  useEffect(() => {
    loadSeasons();
  }, [loadSeasons]);

  const rateSeasonSchema = useMemo(() => z.object({
    name: z.string().trim().max(100, t('common:err_max_length', { count: 100 })).optional(),
    startDate: z.string().min(1, t('common:err_required')),
    endDate: z.string().min(1, t('common:err_required')),
    nightlyPrice: z.number(t('common:err_invalid_number')).positive(t('common:err_must_be_positive')),
  }).refine((data) => data.endDate >= data.startDate, {
    message: t('common:err_invalid_date_range'),
    path: ['endDate'],
  }), [t]);

  const openAddForm = useCallback(() => {
    setEditingSeason(undefined);
    setFormData(EMPTY_FORM);
    setFieldErrors({});
    setMode('form');
  }, []);

  const openEditForm = useCallback((season: RateSeasonResponse) => {
    setEditingSeason(season);
    setFormData({
      name: season.name || '',
      startDate: season.startDate,
      endDate: season.endDate,
      nightlyPrice: season.nightlyPrice,
    });
    setFieldErrors({});
    setMode('form');
  }, []);

  const backToList = useCallback(() => setMode('list'), []);

  const handleChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: name === 'nightlyPrice' ? Number(value) : value }));
  }, []);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});

    const result = rateSeasonSchema.safeParse(formData);
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
      if (editingSeason) {
        await rateSeasonService.updateSeason(roomType.id, editingSeason.id, formData);
      } else {
        await rateSeasonService.createSeason(roomType.id, formData);
      }
      addToast(t('rate_season_saved'), 'success');
      setMode('list');
      await loadSeasons();
    } catch (err: unknown) {
      const e = err as { response?: { status?: number; data?: { detail?: string } } };
      const errorMsg = e.response?.status === 409
        ? t('err_rate_season_overlap')
        : e.response?.data?.detail || t('rate_season_save_failed');
      addToast(errorMsg, 'error');
    } finally {
      setSaving(false);
    }
  }, [formData, editingSeason, roomType.id, rateSeasonSchema, addToast, t, loadSeasons]);

  const tableHeaders = useMemo(() => [
    t('rate_season_name'),
    t('rate_season_start_date'),
    t('rate_season_end_date'),
    t('rate_season_nightly_price'),
    t('actions'),
  ], [t]);

  const openDeleteConfirm = useCallback((season: RateSeasonResponse) => setDeletingSeason(season), []);
  const closeDeleteConfirm = useCallback(() => setDeletingSeason(undefined), []);

  const handleDelete = useCallback(async () => {
    if (!deletingSeason) return;
    setSaving(true);
    try {
      await rateSeasonService.deleteSeason(roomType.id, deletingSeason.id);
      addToast(t('toast_deleted'), 'success');
      await loadSeasons();
    } catch (err: unknown) {
      const e = err as { response?: { data?: { detail?: string } } };
      addToast(e.response?.data?.detail || t('toast_delete_error'), 'error');
    } finally {
      setSaving(false);
      setDeletingSeason(undefined);
    }
  }, [deletingSeason, roomType.id, addToast, t, loadSeasons]);

  const listFooter = (
    <div className="flex justify-end">
      <M3Button variant="text" onClick={onClose}>{t('close')}</M3Button>
    </div>
  );

  const formFooter = (
    <div className="flex justify-end gap-2">
      <M3Button variant="text" onClick={backToList} disabled={saving}>{t('cancel')}</M3Button>
      <M3Button form="rate-season-form" type="submit" loading={saving} disabled={saving}>{t('save')}</M3Button>
    </div>
  );

  return (
    <M3Dialog
      open
      title={t('rate_seasons_title', { roomType: roomType.name })}
      titleId="rate-season-modal-title"
      onClose={onClose}
      footer={mode === 'list' ? listFooter : formFooter}
    >
      {mode === 'list' ? (
        <div className="space-y-4">
          <div className="flex justify-end">
            <M3Button icon="add" onClick={openAddForm}>{t('add_rate_season')}</M3Button>
          </div>

          {loading ? (
            <M3LoadingState label={t('common:loading')} plain className="h-32" />
          ) : seasons.length === 0 ? (
            <M3EmptyState icon="date_range" title={t('no_rate_seasons')} className="py-6" />
          ) : (
            <M3Table headers={tableHeaders}>
              {seasons.map((season) => (
                <SeasonRow key={season.id} season={season} onEdit={openEditForm} onDelete={openDeleteConfirm} t={t} />
              ))}
            </M3Table>
          )}

          {deletingSeason && (
            <M3ConfirmDialog
              title={t('delete_rate_season_title')}
              titleId="rate-season-delete-title"
              message={t('confirm_delete_rate_season')}
              confirmLabel={t('btn_confirm')}
              onConfirm={handleDelete}
              onCancel={closeDeleteConfirm}
              loading={saving}
            />
          )}
        </div>
      ) : (
        <form id="rate-season-form" onSubmit={handleSubmit} noValidate className="space-y-4">
          <M3TextField
            label={t('rate_season_name')}
            name="name"
            value={formData.name}
            onChange={handleChange}
            placeholder={t('rate_season_name_placeholder')}
            errorText={fieldErrors.name}
          />

          <div className="grid grid-cols-2 gap-4">
            <M3TextField
              label={`${t('rate_season_start_date')} *`}
              required
              type="date"
              name="startDate"
              value={formData.startDate}
              onChange={handleChange}
              errorText={fieldErrors.startDate}
            />
            <M3TextField
              label={`${t('rate_season_end_date')} *`}
              required
              type="date"
              name="endDate"
              value={formData.endDate}
              onChange={handleChange}
              errorText={fieldErrors.endDate}
            />
          </div>

          <M3TextField
            label={`${t('rate_season_nightly_price')} *`}
            required
            type="number"
            min="0.01"
            step="0.01"
            name="nightlyPrice"
            value={formData.nightlyPrice}
            onChange={handleChange}
            errorText={fieldErrors.nightlyPrice}
          />
        </form>
      )}
    </M3Dialog>
  );
});

RateSeasonManagerModal.displayName = 'RateSeasonManagerModal';
