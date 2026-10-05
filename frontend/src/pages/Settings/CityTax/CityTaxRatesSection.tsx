import { useState, useEffect, useCallback, useMemo, memo, type ChangeEvent, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { useFormatters } from '../../../hooks';
import { stayService } from '../../../services';
import type { CityTaxRateResponse } from '../../../types';
import { M3Button } from '../../../components/m3';
import { M3Card } from '../../../components/m3';
import { M3EmptyState } from '../../../components/m3';
import { M3LoadingState } from '../../../components/m3';
import { M3Table, M3TableRow, M3TableCell } from '../../../components/m3';
import { M3TextField } from '../../../components/m3';
import { useToastStore } from '../../../store';
import { getErrorMessage } from '../../../utils';
import { CATEGORY_MAX_LENGTH, zodFieldErrors } from './cityTaxForm';

const NOTE_MAX_LENGTH = 200;
const AGE_MIN = 0;
const AGE_MAX = 120;

const EMPTY_RATE_FORM = {
  category: '', amountPerNight: '', maxTaxableNights: '', exemptUnderAge: '', validFrom: '', note: '',
};
type RateFormState = typeof EMPTY_RATE_FORM;

const RateRow = memo(({ rate }: { rate: CityTaxRateResponse }) => {
  const { formatCurrency } = useFormatters();
  return (
  <M3TableRow>
    <M3TableCell className="font-medium">{rate.category}</M3TableCell>
    <M3TableCell>{formatCurrency(rate.amountPerNight)}</M3TableCell>
    <M3TableCell>{rate.maxTaxableNights ?? '-'}</M3TableCell>
    <M3TableCell>{rate.exemptUnderAge ?? '-'}</M3TableCell>
    <M3TableCell>{rate.validFrom}</M3TableCell>
    <M3TableCell>{rate.validTo ?? '-'}</M3TableCell>
  </M3TableRow>
  );
});
RateRow.displayName = 'RateRow';

export const CityTaxRatesSection = () => {
  const { t } = useTranslation(['settings', 'common']);
  const addToast = useToastStore((s) => s.addToast);

  const [rates, setRates] = useState<CityTaxRateResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [formData, setFormData] = useState<RateFormState>(EMPTY_RATE_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const loadRates = useCallback(async () => {
    try {
      setLoading(true);
      const data = await stayService.getCityTaxRates();
      setRates(data);
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('city_tax_err_loading_rates')), 'error');
    } finally {
      setLoading(false);
    }
  }, [addToast, t]);

  useEffect(() => {
    loadRates();
  }, [loadRates]);

  const schema = useMemo(() => z.object({
    category: z.string().trim().min(1, t('common:err_required')).max(
      CATEGORY_MAX_LENGTH, t('common:err_max_length', { count: CATEGORY_MAX_LENGTH }),
    ),
    amountPerNight: z.number(t('common:err_invalid_number')).min(0, t('common:err_must_be_positive')),
    maxTaxableNights: z.number(t('common:err_invalid_number')).int().positive(t('common:err_must_be_positive')).optional(),
    exemptUnderAge: z.number(t('common:err_invalid_number')).int()
      .min(AGE_MIN, t('city_tax_err_invalid_age')).max(AGE_MAX, t('city_tax_err_invalid_age')).optional(),
    validFrom: z.string().min(1, t('common:err_required')),
    note: z.string().trim().max(NOTE_MAX_LENGTH, t('common:err_max_length', { count: NOTE_MAX_LENGTH })).optional(),
  }), [t]);

  const handleChange = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  }, []);

  const handleSubmit = useCallback(async (e: FormEvent) => {
    e.preventDefault();
    setFieldErrors({});

    const parsed = {
      category: formData.category,
      amountPerNight: formData.amountPerNight === '' ? Number.NaN : Number(formData.amountPerNight),
      maxTaxableNights: formData.maxTaxableNights === '' ? undefined : Number(formData.maxTaxableNights),
      exemptUnderAge: formData.exemptUnderAge === '' ? undefined : Number(formData.exemptUnderAge),
      validFrom: formData.validFrom,
      note: formData.note === '' ? undefined : formData.note,
    };
    const result = schema.safeParse(parsed);
    if (!result.success) {
      setFieldErrors(zodFieldErrors(result.error));
      return;
    }

    setSaving(true);
    try {
      await stayService.createCityTaxRate(result.data);
      addToast(t('save'), 'success');
      setFormData(EMPTY_RATE_FORM);
      await loadRates();
    } catch (err: unknown) {
      // Backend distinguishes CITY_TAX_RATE_OVERLAP (409), CITY_TAX_COMUNE_NOT_CONFIGURED
      // (400) and CITY_TAX_RATE_VALID_FROM_NOT_AFTER_CURRENT (400) by error code, not by
      // status alone — two different 400s exist, so branching on status here previously
      // mislabelled the validFrom-guard error as "comune not configured". The response
      // interceptor already translates the code via locales/*/errors.json; just use it.
      addToast(getErrorMessage(err, t('city_tax_err_save')), 'error');
    } finally {
      setSaving(false);
    }
  }, [formData, schema, addToast, t, loadRates]);

  const tableHeaders = useMemo(() => [
    t('city_tax_category'),
    t('city_tax_amount_per_night'),
    t('city_tax_max_taxable_nights'),
    t('city_tax_exempt_under_age'),
    t('city_tax_valid_from'),
    t('city_tax_valid_to'),
  ], [t]);

  return (
    <M3Card variant="solid" className="p-6 space-y-4">
      <div>
        <h2 className="text-sm font-semibold text-on-surface">{t('city_tax_rates_section_title')}</h2>
        <p className="text-xs text-on-surface-variant mt-0.5">{t('city_tax_rates_section_desc')}</p>
      </div>

      <form onSubmit={handleSubmit} noValidate className="grid grid-cols-2 gap-4">
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
          label={`${t('city_tax_amount_per_night')} *`}
          required
          type="number"
          min="0"
          step="0.01"
          name="amountPerNight"
          value={formData.amountPerNight}
          onChange={handleChange}
          errorText={fieldErrors.amountPerNight}
        />

        <M3TextField
          label={t('city_tax_max_taxable_nights')}
          type="number"
          min="1"
          step="1"
          name="maxTaxableNights"
          value={formData.maxTaxableNights}
          onChange={handleChange}
          placeholder={t('city_tax_uncapped_placeholder')}
          errorText={fieldErrors.maxTaxableNights}
        />

        <M3TextField
          label={t('city_tax_exempt_under_age')}
          type="number"
          min={AGE_MIN}
          max={AGE_MAX}
          step="1"
          name="exemptUnderAge"
          value={formData.exemptUnderAge}
          onChange={handleChange}
          placeholder={t('city_tax_no_age_exemption_placeholder')}
          errorText={fieldErrors.exemptUnderAge}
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

        <M3TextField
          label={t('city_tax_note')}
          name="note"
          value={formData.note}
          onChange={handleChange}
          placeholder={t('city_tax_note_placeholder')}
          errorText={fieldErrors.note}
        />

        <div className="col-span-2 flex justify-end">
          <M3Button type="submit" icon="add" loading={saving} disabled={saving}>
            {t('city_tax_add_rate')}
          </M3Button>
        </div>
      </form>

      {loading ? (
        <M3LoadingState label={t('common:loading')} plain className="h-24" />
      ) : rates.length === 0 ? (
        <M3EmptyState icon="payments" title={t('city_tax_no_rates')} className="py-4" />
      ) : (
        <M3Table headers={tableHeaders}>
          {rates.map((rate) => <RateRow key={rate.id} rate={rate} />)}
        </M3Table>
      )}
    </M3Card>
  );
};
