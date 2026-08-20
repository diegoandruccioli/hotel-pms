import { useState, useEffect, useCallback, useMemo, memo, type ChangeEvent, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { stayService } from '../../services/stayService';
import type {
  CityTaxRateResponse,
  HotelCategoryHistoryRequest,
  HotelCategoryHistoryResponse,
} from '../../types/stay.types';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3/M3Button';
import { M3Card } from '../../components/m3/M3Card';
import { M3Table, M3TableRow, M3TableCell } from '../../components/m3/M3Table';
import { SettingsPageHeader } from '../../components/SettingsPageHeader';
import { useToastStore } from '../../store/toastStore';
import { getErrorMessage } from '../../utils/errorMessage';

const CATEGORY_MAX_LENGTH = 20;
const NOTE_MAX_LENGTH = 200;
const AGE_MIN = 0;
const AGE_MAX = 120;

const inputClass = 'block w-full rounded-shape-xs border border-outline px-3 py-2 text-sm font-body '
  + 'bg-transparent text-on-surface focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none';

// -----------------------------------------------------------------------
// Hotel category history section
// -----------------------------------------------------------------------

const EMPTY_CATEGORY_FORM: HotelCategoryHistoryRequest = { category: '', validFrom: '' };

const CategoryRow = memo(({ entry }: { entry: HotelCategoryHistoryResponse }) => (
  <M3TableRow>
    <M3TableCell className="font-medium">{entry.category}</M3TableCell>
    <M3TableCell>{entry.validFrom}</M3TableCell>
    <M3TableCell>{entry.validTo ?? '-'}</M3TableCell>
  </M3TableRow>
));
CategoryRow.displayName = 'CategoryRow';

const HotelCategorySection = () => {
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
    <M3Card className="p-6 space-y-4">
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
        <div>
          <label htmlFor="hc-category" className="block text-sm font-medium font-body text-on-surface-variant mb-1">
            {t('city_tax_category')} *
          </label>
          <input
            type="text"
            id="hc-category"
            name="category"
            value={formData.category}
            onChange={handleChange}
            placeholder={t('city_tax_category_placeholder')}
            className={inputClass}
            aria-invalid={!!fieldErrors.category}
            aria-describedby={fieldErrors.category ? 'hc-category-error' : undefined}
          />
          {fieldErrors.category && (
            <p id="hc-category-error" role="alert" className="mt-1 text-sm font-body text-error">{fieldErrors.category}</p>
          )}
        </div>
        <div>
          <label htmlFor="hc-valid-from" className="block text-sm font-medium font-body text-on-surface-variant mb-1">
            {t('city_tax_valid_from')} *
          </label>
          <input
            type="date"
            id="hc-valid-from"
            name="validFrom"
            value={formData.validFrom}
            onChange={handleChange}
            className={inputClass}
            aria-invalid={!!fieldErrors.validFrom}
            aria-describedby={fieldErrors.validFrom ? 'hc-valid-from-error' : undefined}
          />
          {fieldErrors.validFrom && (
            <p id="hc-valid-from-error" role="alert" className="mt-1 text-sm font-body text-error">{fieldErrors.validFrom}</p>
          )}
        </div>
        <div className="col-span-2 flex justify-end">
          <M3Button type="submit" icon="add" loading={saving} disabled={saving}>
            {t('city_tax_add_category')}
          </M3Button>
        </div>
      </form>

      {loading ? (
        <div className="flex justify-center items-center h-24">
          <MaterialIcon name="progress_activity" size={28} className="text-primary animate-spin" />
        </div>
      ) : history.length === 0 ? (
        <p className="text-sm font-body text-on-surface-variant py-4">{t('city_tax_no_category_history')}</p>
      ) : (
        <M3Table headers={tableHeaders}>
          {history.map((entry) => <CategoryRow key={entry.id} entry={entry} />)}
        </M3Table>
      )}
    </M3Card>
  );
};

// -----------------------------------------------------------------------
// City tax rates section
// -----------------------------------------------------------------------

const EMPTY_RATE_FORM = {
  category: '', amountPerNight: '', maxTaxableNights: '', exemptUnderAge: '', validFrom: '', note: '',
};
type RateFormState = typeof EMPTY_RATE_FORM;

const RateRow = memo(({ rate }: { rate: CityTaxRateResponse }) => (
  <M3TableRow>
    <M3TableCell className="font-medium">{rate.category}</M3TableCell>
    <M3TableCell>€ {rate.amountPerNight.toFixed(2)}</M3TableCell>
    <M3TableCell>{rate.maxTaxableNights ?? '-'}</M3TableCell>
    <M3TableCell>{rate.exemptUnderAge ?? '-'}</M3TableCell>
    <M3TableCell>{rate.validFrom}</M3TableCell>
    <M3TableCell>{rate.validTo ?? '-'}</M3TableCell>
  </M3TableRow>
));
RateRow.displayName = 'RateRow';

const CityTaxRatesSection = () => {
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
      await stayService.createCityTaxRate(result.data);
      addToast(t('save'), 'success');
      setFormData(EMPTY_RATE_FORM);
      await loadRates();
    } catch (err: unknown) {
      const e = err as { response?: { status?: number } };
      const errorMsg = e.response?.status === 409
        ? t('city_tax_err_overlap')
        : e.response?.status === 400
          ? t('city_tax_err_comune_not_configured')
          : getErrorMessage(err, t('city_tax_err_save'));
      addToast(errorMsg, 'error');
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
    <M3Card className="p-6 space-y-4">
      <div>
        <h2 className="text-sm font-semibold text-on-surface">{t('city_tax_rates_section_title')}</h2>
        <p className="text-xs text-on-surface-variant mt-0.5">{t('city_tax_rates_section_desc')}</p>
      </div>

      <form onSubmit={handleSubmit} noValidate className="grid grid-cols-2 gap-4">
        <div>
          <label htmlFor="ctr-category" className="block text-sm font-medium font-body text-on-surface-variant mb-1">
            {t('city_tax_category')} *
          </label>
          <input
            type="text"
            id="ctr-category"
            name="category"
            value={formData.category}
            onChange={handleChange}
            placeholder={t('city_tax_category_placeholder')}
            className={inputClass}
            aria-invalid={!!fieldErrors.category}
            aria-describedby={fieldErrors.category ? 'ctr-category-error' : undefined}
          />
          {fieldErrors.category && (
            <p id="ctr-category-error" role="alert" className="mt-1 text-sm font-body text-error">{fieldErrors.category}</p>
          )}
        </div>

        <div>
          <label htmlFor="ctr-amount" className="block text-sm font-medium font-body text-on-surface-variant mb-1">
            {t('city_tax_amount_per_night')} *
          </label>
          <input
            type="number"
            min="0"
            step="0.01"
            id="ctr-amount"
            name="amountPerNight"
            value={formData.amountPerNight}
            onChange={handleChange}
            className={inputClass}
            aria-invalid={!!fieldErrors.amountPerNight}
            aria-describedby={fieldErrors.amountPerNight ? 'ctr-amount-error' : undefined}
          />
          {fieldErrors.amountPerNight && (
            <p id="ctr-amount-error" role="alert" className="mt-1 text-sm font-body text-error">{fieldErrors.amountPerNight}</p>
          )}
        </div>

        <div>
          <label htmlFor="ctr-max-nights" className="block text-sm font-medium font-body text-on-surface-variant mb-1">
            {t('city_tax_max_taxable_nights')}
          </label>
          <input
            type="number"
            min="1"
            step="1"
            id="ctr-max-nights"
            name="maxTaxableNights"
            value={formData.maxTaxableNights}
            onChange={handleChange}
            placeholder={t('city_tax_uncapped_placeholder')}
            className={inputClass}
            aria-invalid={!!fieldErrors.maxTaxableNights}
            aria-describedby={fieldErrors.maxTaxableNights ? 'ctr-max-nights-error' : undefined}
          />
          {fieldErrors.maxTaxableNights && (
            <p id="ctr-max-nights-error" role="alert" className="mt-1 text-sm font-body text-error">{fieldErrors.maxTaxableNights}</p>
          )}
        </div>

        <div>
          <label htmlFor="ctr-exempt-age" className="block text-sm font-medium font-body text-on-surface-variant mb-1">
            {t('city_tax_exempt_under_age')}
          </label>
          <input
            type="number"
            min={AGE_MIN}
            max={AGE_MAX}
            step="1"
            id="ctr-exempt-age"
            name="exemptUnderAge"
            value={formData.exemptUnderAge}
            onChange={handleChange}
            placeholder={t('city_tax_no_age_exemption_placeholder')}
            className={inputClass}
            aria-invalid={!!fieldErrors.exemptUnderAge}
            aria-describedby={fieldErrors.exemptUnderAge ? 'ctr-exempt-age-error' : undefined}
          />
          {fieldErrors.exemptUnderAge && (
            <p id="ctr-exempt-age-error" role="alert" className="mt-1 text-sm font-body text-error">{fieldErrors.exemptUnderAge}</p>
          )}
        </div>

        <div>
          <label htmlFor="ctr-valid-from" className="block text-sm font-medium font-body text-on-surface-variant mb-1">
            {t('city_tax_valid_from')} *
          </label>
          <input
            type="date"
            id="ctr-valid-from"
            name="validFrom"
            value={formData.validFrom}
            onChange={handleChange}
            className={inputClass}
            aria-invalid={!!fieldErrors.validFrom}
            aria-describedby={fieldErrors.validFrom ? 'ctr-valid-from-error' : undefined}
          />
          {fieldErrors.validFrom && (
            <p id="ctr-valid-from-error" role="alert" className="mt-1 text-sm font-body text-error">{fieldErrors.validFrom}</p>
          )}
        </div>

        <div>
          <label htmlFor="ctr-note" className="block text-sm font-medium font-body text-on-surface-variant mb-1">
            {t('city_tax_note')}
          </label>
          <input
            type="text"
            id="ctr-note"
            name="note"
            value={formData.note}
            onChange={handleChange}
            placeholder={t('city_tax_note_placeholder')}
            className={inputClass}
            aria-invalid={!!fieldErrors.note}
            aria-describedby={fieldErrors.note ? 'ctr-note-error' : undefined}
          />
          {fieldErrors.note && (
            <p id="ctr-note-error" role="alert" className="mt-1 text-sm font-body text-error">{fieldErrors.note}</p>
          )}
        </div>

        <div className="col-span-2 flex justify-end">
          <M3Button type="submit" icon="add" loading={saving} disabled={saving}>
            {t('city_tax_add_rate')}
          </M3Button>
        </div>
      </form>

      {loading ? (
        <div className="flex justify-center items-center h-24">
          <MaterialIcon name="progress_activity" size={28} className="text-primary animate-spin" />
        </div>
      ) : rates.length === 0 ? (
        <p className="text-sm font-body text-on-surface-variant py-4">{t('city_tax_no_rates')}</p>
      ) : (
        <M3Table headers={tableHeaders}>
          {rates.map((rate) => <RateRow key={rate.id} rate={rate} />)}
        </M3Table>
      )}
    </M3Card>
  );
};

// -----------------------------------------------------------------------
// SettingsCityTax page
// -----------------------------------------------------------------------

export const SettingsCityTax = () => {
  const { t } = useTranslation('settings');
  const navigate = useNavigate();
  const handleBack = useCallback(() => navigate(-1), [navigate]);

  return (
    <div className="space-y-6 max-w-3xl mx-auto pb-10">
      <SettingsPageHeader
        icon="account_balance"
        title={t('settings_section_city_tax')}
        subtitle={t('city_tax_page_subtitle')}
        onBack={handleBack}
      />
      <HotelCategorySection />
      <CityTaxRatesSection />
    </div>
  );
};
