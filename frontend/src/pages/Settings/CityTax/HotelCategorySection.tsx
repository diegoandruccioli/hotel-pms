import { useState, useEffect, useCallback, useMemo, memo, type ChangeEvent, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { stayService } from '../../../services';
import type { HotelCategoryHistoryRequest, HotelCategoryHistoryResponse } from '../../../types';
import { M3Button } from '../../../components/m3';
import { M3Card } from '../../../components/m3';
import { M3EmptyState } from '../../../components/m3';
import { M3LoadingState } from '../../../components/m3';
import { M3Table, M3TableRow, M3TableCell } from '../../../components/m3';
import { M3TextField } from '../../../components/m3';
import { useToastStore } from '../../../store';
import { getErrorMessage } from '../../../utils';
import { CATEGORY_MAX_LENGTH, zodFieldErrors } from './cityTaxForm';

const EMPTY_CATEGORY_FORM: HotelCategoryHistoryRequest = { category: '', validFrom: '' };

const CategoryRow = memo(({ entry }: { entry: HotelCategoryHistoryResponse }) => (
  <M3TableRow>
    <M3TableCell className="font-medium">{entry.category}</M3TableCell>
    <M3TableCell>{entry.validFrom}</M3TableCell>
    <M3TableCell>{entry.validTo ?? '-'}</M3TableCell>
  </M3TableRow>
));
CategoryRow.displayName = 'CategoryRow';

export const HotelCategorySection = () => {
  const { t } = useTranslation(['settings', 'common']);
  const addToast = useToastStore((s) => s.addToast);

  const [history, setHistory] = useState<HotelCategoryHistoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [formData, setFormData] = useState<HotelCategoryHistoryRequest>(EMPTY_CATEGORY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const loadHistory = useCallback(async () => {
    try {
      setLoading(true);
      const data = await stayService.getHotelCategoryHistory();
      setHistory(data);
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('city_tax_err_loading_category')), 'error');
    } finally {
      setLoading(false);
    }
  }, [addToast, t]);

  useEffect(() => {
    loadHistory();
  }, [loadHistory]);

  const schema = useMemo(() => z.object({
    category: z.string().trim().min(1, t('common:err_required')).max(
      CATEGORY_MAX_LENGTH, t('common:err_max_length', { count: CATEGORY_MAX_LENGTH }),
    ),
    validFrom: z.string().min(1, t('common:err_required')),
  }), [t]);

  const handleChange = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  }, []);

  const handleSubmit = useCallback(async (e: FormEvent) => {
    e.preventDefault();
    setFieldErrors({});

    const result = schema.safeParse(formData);
    if (!result.success) {
      setFieldErrors(zodFieldErrors(result.error));
      return;
    }

    setSaving(true);
    try {
      await stayService.recordHotelCategory(result.data);
      addToast(t('save'), 'success');
      setFormData(EMPTY_CATEGORY_FORM);
      await loadHistory();
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('city_tax_err_save')), 'error');
    } finally {
      setSaving(false);
    }
  }, [formData, schema, addToast, t, loadHistory]);

  const tableHeaders = useMemo(() => [
    t('city_tax_category'),
    t('city_tax_valid_from'),
    t('city_tax_valid_to'),
  ], [t]);

  const currentCategory = history.find((h) => h.validTo === null);

  return (
    <M3Card variant="solid" className="p-6 space-y-4">
      <div>
        <h2 className="text-sm font-semibold text-on-surface">{t('city_tax_category_section_title')}</h2>
        <p className="text-xs text-on-surface-variant mt-0.5">{t('city_tax_category_section_desc')}</p>
      </div>

      {currentCategory && (
        <p className="text-sm font-medium text-primary">
          {t('city_tax_current_category', { category: currentCategory.category })}
        </p>
      )}

      <form onSubmit={handleSubmit} noValidate className="grid grid-cols-2 gap-4 items-end">
        <M3TextField
          label={`${t('city_tax_category')} *`}
          required
          name="category"
          value={formData.category}
          onChange={handleChange}
          placeholder={t('city_tax_category_placeholder')}
          errorText={fieldErrors.category}
        />
        <M3TextField
          label={`${t('city_tax_valid_from')} *`}
          required
          type="date"
          name="validFrom"
          value={formData.validFrom}
          onChange={handleChange}
          errorText={fieldErrors.validFrom}
        />
        <div className="col-span-2 flex justify-end">
          <M3Button type="submit" icon="add" loading={saving} disabled={saving}>
            {t('city_tax_add_category')}
          </M3Button>
        </div>
      </form>

      {loading ? (
        <M3LoadingState label={t('common:loading')} plain className="h-24" />
      ) : history.length === 0 ? (
        <M3EmptyState icon="history" title={t('city_tax_no_category_history')} className="py-4" />
      ) : (
        <M3Table headers={tableHeaders}>
          {history.map((entry) => <CategoryRow key={entry.id} entry={entry} />)}
        </M3Table>
      )}
    </M3Card>
  );
};
