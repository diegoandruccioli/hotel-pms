import { useState, useEffect, useCallback, useMemo, type ChangeEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { stayService } from '../../../services';
import type { CityTaxApplicability } from '../../../types';
import { M3Card } from '../../../components/m3';
import { M3LoadingState } from '../../../components/m3';
import { M3Select } from '../../../components/m3';
import { useToastStore } from '../../../store';
import { getErrorMessage } from '../../../utils';

/** The tri-state that lets a hotel in a comune with no tourist tax silence the "not configured"
 * warnings for good (Parte 5.2), without that silence also hiding a hotel that simply forgot to
 * configure a real rate. */
export const CityTaxApplicabilitySection = () => {
  const { t } = useTranslation(['settings', 'common']);
  const addToast = useToastStore((s) => s.addToast);

  const [applicability, setApplicability] = useState<CityTaxApplicability | ''>('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    stayService.getCityTaxApplicability()
      .then((data) => {
        if (!cancelled) setApplicability(data.applicability);
      })
      .catch((err: unknown) => {
        if (!cancelled) addToast(getErrorMessage(err, t('city_tax_err_loading_applicability')), 'error');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [addToast, t]);

  const options = useMemo(() => [
    { value: 'UNKNOWN', label: t('city_tax_applicability_unknown') },
    { value: 'APPLICABLE', label: t('city_tax_applicability_applicable') },
    { value: 'NOT_APPLICABLE', label: t('city_tax_applicability_not_applicable') },
  ], [t]);

  const handleChange = useCallback(async (e: ChangeEvent<HTMLSelectElement>) => {
    const next = e.target.value as CityTaxApplicability;
    const previous = applicability;
    setApplicability(next);
    setSaving(true);
    try {
      await stayService.updateCityTaxApplicability({ applicability: next });
      addToast(t('save'), 'success');
    } catch (err: unknown) {
      setApplicability(previous);
      addToast(getErrorMessage(err, t('city_tax_err_save')), 'error');
    } finally {
      setSaving(false);
    }
  }, [applicability, addToast, t]);

  return (
    <M3Card variant="solid" className="p-6 space-y-4">
      <div>
        <h2 className="text-sm font-semibold text-on-surface">{t('city_tax_applicability_section_title')}</h2>
        <p className="text-xs text-on-surface-variant mt-0.5">{t('city_tax_applicability_section_desc')}</p>
      </div>

      {loading ? (
        <M3LoadingState label={t('common:loading')} plain className="h-16" />
      ) : (
        <M3Select
          label={t('city_tax_applicability_label')}
          options={options}
          value={applicability}
          onChange={handleChange}
          disabled={saving}
          className="max-w-sm"
        />
      )}
    </M3Card>
  );
};
