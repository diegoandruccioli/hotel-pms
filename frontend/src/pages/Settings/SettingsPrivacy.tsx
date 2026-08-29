import { useState, useEffect, useCallback, type ChangeEvent, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { guestService } from '../../services/guestService';
import type { GuestPrivacySettingsResponse } from '../../types/guest.types';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3/M3Button';
import { M3Card } from '../../components/m3/M3Card';
import { M3TextField } from '../../components/m3/M3TextField';
import { SettingsPageHeader } from '../../components/SettingsPageHeader';
import { useToastStore } from '../../store/toastStore';
import { getErrorMessage } from '../../utils/errorMessage';

// -----------------------------------------------------------------------
// SettingsPrivacy page — GDPR retention settings (T-GST-05). The backend
// already exposes GET/PUT /api/v1/guests/settings and GET /api/v1/guests/{id}/export
// (see GuestPrivacySettingsController, GuestController#exportGuestData); this
// page was the missing piece — an Admin/Owner previously had no UI to reach
// either, only a direct API call.
// -----------------------------------------------------------------------

export const SettingsPrivacy = () => {
  const { t } = useTranslation(['settings', 'common']);
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const handleBack = useCallback(() => navigate(-1), [navigate]);

  const [settings, setSettings] = useState<GuestPrivacySettingsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [retentionYears, setRetentionYears] = useState('');
  const [fieldError, setFieldError] = useState('');

  const loadSettings = useCallback(async () => {
    try {
      setLoading(true);
      const data = await guestService.getPrivacySettings();
      setSettings(data);
      setRetentionYears(String(data.guestRetentionYears));
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('privacy_err_loading')), 'error');
    } finally {
      setLoading(false);
    }
  }, [addToast, t]);

  useEffect(() => {
    loadSettings();
  }, [loadSettings]);

  const handleChange = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    setRetentionYears(e.target.value);
  }, []);

  const handleSubmit = useCallback(async (e: FormEvent) => {
    e.preventDefault();
    setFieldError('');
    if (!settings) return;

    const years = Number(retentionYears);
    // Mirrors the backend's own floor (GuestPrivacySettingsRequest requires
    // >= TULPS_MIN_YEARS) — fiscalMinYears is informational only here, since
    // fiscal-document retention is a billing-service concern, not this field.
    if (!Number.isInteger(years) || years < settings.tulpsMinYears) {
      setFieldError(t('privacy_err_min_years', { count: settings.tulpsMinYears }));
      return;
    }

    setSaving(true);
    try {
      const updated = await guestService.updatePrivacySettings({ guestRetentionYears: years });
      setSettings(updated);
      setRetentionYears(String(updated.guestRetentionYears));
      addToast(t('save'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('privacy_err_save')), 'error');
    } finally {
      setSaving(false);
    }
  }, [settings, retentionYears, addToast, t]);

  return (
    <div className="space-y-6 max-w-3xl mx-auto pb-10">
      <SettingsPageHeader
        icon="privacy_tip"
        title={t('settings_section_privacy')}
        subtitle={t('privacy_page_subtitle')}
        onBack={handleBack}
      />

      <M3Card className="p-6 space-y-4">
        <div>
          <h2 className="text-sm font-semibold text-on-surface">{t('privacy_retention_section_title')}</h2>
          <p className="text-xs text-on-surface-variant mt-0.5">{t('privacy_retention_section_desc')}</p>
        </div>

        {loading ? (
          <div className="flex justify-center items-center h-24">
            <MaterialIcon name="progress_activity" size={28} className="text-primary animate-spin" />
          </div>
        ) : settings && (
          <>
            <form onSubmit={handleSubmit} noValidate className="grid grid-cols-2 gap-4 items-end">
              <M3TextField
                label={`${t('privacy_retention_years')} *`}
                required
                type="number"
                min={settings.tulpsMinYears}
                step="1"
                name="guestRetentionYears"
                value={retentionYears}
                onChange={handleChange}
                supportingText={t('privacy_retention_years_helper', { count: settings.tulpsMinYears })}
                errorText={fieldError}
              />
              <div className="flex justify-end">
                <M3Button type="submit" icon="save" loading={saving} disabled={saving}>
                  {t('save')}
                </M3Button>
              </div>
            </form>

            <dl className="grid grid-cols-2 gap-4 pt-2 border-t border-outline-variant/30 text-sm">
              <div>
                <dt className="text-on-surface-variant">{t('privacy_tulps_min_label')}</dt>
                <dd className="font-medium text-on-surface">
                  {t('privacy_years_value', { count: settings.tulpsMinYears })}
                </dd>
              </div>
              <div>
                <dt className="text-on-surface-variant">{t('privacy_fiscal_min_label')}</dt>
                <dd className="font-medium text-on-surface">
                  {t('privacy_years_value', { count: settings.fiscalMinYears })}
                </dd>
              </div>
            </dl>
          </>
        )}
      </M3Card>
    </div>
  );
};
