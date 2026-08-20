import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { stayService } from '../services/stayService';
import type { HotelSettingsResponse, HotelSettingsRequest } from '../types/stay.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3Card } from '../components/m3/M3Card';
import { M3TextField } from '../components/m3/M3TextField';
import { M3Checkbox } from '../components/m3/M3Checkbox';
import { StructuredAddressFields } from '../components/StructuredAddressFields';
import { useToastStore } from '../store/toastStore';
import { getErrorMessage } from '../utils/errorMessage';

const VAT_NUMBER_REGEX = /^\d{11}$/;
const FISCAL_CODE_REGEX = /^(\d{11}|[A-Za-z]{6}\d{2}[A-Za-z]\d{2}[A-Za-z]\d{3}[A-Za-z])$/;

// -----------------------------------------------------------------------
// HotelProfile page
// -----------------------------------------------------------------------

export function HotelProfile() {
  const { t } = useTranslation('admin');
  const { addToast } = useToastStore();

  const [form, setForm] = useState<HotelSettingsRequest>({
    alloggiatiAutoSend: false,
    hotelName: '',
    address: '',
    vatNumber: '',
    fiscalCode: '',
    logoUrl: '',
    alloggiatiUsername: '',
    alloggiatiPassword: '',
    alloggiatiWsKey: '',
    cap: '',
    comune: '',
    provincia: '',
  });
  const [credentialsConfigured, setCredentialsConfigured] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const imgRef = useRef<HTMLImageElement>(null);

  const profileSchema = useMemo(() => z.object({
    vatNumber: z.union([z.string().regex(VAT_NUMBER_REGEX, t('common:err_invalid_vat')), z.literal('')]),
    fiscalCode: z.union([z.string().regex(FISCAL_CODE_REGEX, t('common:err_invalid_fiscal_code')), z.literal('')]),
    logoUrl: z.union([z.string().url(t('common:err_invalid_url')), z.literal('')]),
  }), [t]);
  const handleLogoError = useCallback(() => {
    if (imgRef.current) imgRef.current.style.display = 'none';
  }, []);

  useEffect(() => {
    stayService
      .getHotelSettings()
      .then((s: HotelSettingsResponse) => {
        setForm({
          alloggiatiAutoSend: s.alloggiatiAutoSend,
          hotelName: s.hotelName ?? '',
          address: s.address ?? '',
          vatNumber: s.vatNumber ?? '',
          fiscalCode: s.fiscalCode ?? '',
          logoUrl: s.logoUrl ?? '',
          // Password/WsKey are write-only — the API never returns them, so the
          // fields always start blank regardless of whether credentials exist.
          alloggiatiUsername: s.alloggiatiUsername ?? '',
          alloggiatiPassword: '',
          alloggiatiWsKey: '',
          cap: s.cap ?? '',
          comune: s.comune ?? '',
          provincia: s.provincia ?? '',
        });
        setCredentialsConfigured(s.alloggiatiCredentialsConfigured);
      })
      .catch(() => addToast(t('err_profile_save'), 'error'))
      .finally(() => setLoading(false));
  }, [addToast, t]);

  const handleChange = useCallback(
    (field: keyof HotelSettingsRequest) =>
      (e: React.ChangeEvent<HTMLInputElement>) =>
        setForm((prev) => ({ ...prev, [field]: e.target.value })),
    [],
  );

  const handleToggle = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm((prev) => ({ ...prev, alloggiatiAutoSend: e.target.checked })),
    [],
  );

  const handleCapChange = useCallback(
    (value: string) => setForm((prev) => ({ ...prev, cap: value })),
    [],
  );
  const handleComuneChange = useCallback(
    (value: string) => setForm((prev) => ({ ...prev, comune: value })),
    [],
  );
  const handleProvinciaChange = useCallback(
    (value: string) => setForm((prev) => ({ ...prev, provincia: value })),
    [],
  );

  const handleSave = useCallback(async () => {
    setFieldErrors({});

    const result = profileSchema.safeParse({
      vatNumber: (form.vatNumber ?? '').trim(),
      fiscalCode: (form.fiscalCode ?? '').trim(),
      logoUrl: (form.logoUrl ?? '').trim(),
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
      const updated = await stayService.updateHotelSettings({ ...form, ...result.data });
      setForm((prev) => ({ ...prev, alloggiatiPassword: '', alloggiatiWsKey: '' }));
      setCredentialsConfigured(updated.alloggiatiCredentialsConfigured);
      addToast(t('toast_profile_saved'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('err_profile_save')), 'error');
    } finally {
      setSaving(false);
    }
  }, [form, profileSchema, addToast, t]);

  if (loading) {
    return (
      <div className="flex justify-center py-24">
        <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
      </div>
    );
  }

  return (
    <main className="max-w-xl mx-auto p-6 space-y-6" aria-labelledby="hotel-profile-title">
      <div>
        <h1 id="hotel-profile-title" className="text-2xl font-semibold text-on-surface flex items-center gap-2">
          <MaterialIcon name="apartment" className="text-primary" />
          {t('hotel_profile_title')}
        </h1>
        <p className="text-sm text-on-surface-variant mt-1">{t('hotel_profile_subtitle')}</p>
      </div>

      <M3Card className="p-6 space-y-4">
        {/* Logo preview */}
        {form.logoUrl && (
          <div className="flex justify-center mb-2">
            <img
              ref={imgRef}
              src={form.logoUrl}
              alt="hotel logo preview"
              className="max-h-20 object-contain rounded-md border border-outline-variant"
              onError={handleLogoError}
            />
          </div>
        )}

        <M3TextField
          label={t('label_hotel_name')}
          value={form.hotelName ?? ''}
          placeholder={t('placeholder_hotel_name')}
          onChange={handleChange('hotelName')}
        />

        <M3TextField
          label={t('label_hotel_address')}
          value={form.address ?? ''}
          placeholder={t('placeholder_address')}
          onChange={handleChange('address')}
        />

        <StructuredAddressFields
          idPrefix="profile"
          cap={form.cap ?? ''}
          comune={form.comune ?? ''}
          provincia={form.provincia ?? ''}
          onCapChange={handleCapChange}
          onComuneChange={handleComuneChange}
          onProvinciaChange={handleProvinciaChange}
        />

        <div className="grid grid-cols-2 gap-4">
          <M3TextField
            label={t('label_vat_number')}
            value={form.vatNumber ?? ''}
            placeholder={t('placeholder_vat_number')}
            onChange={handleChange('vatNumber')}
            errorText={fieldErrors.vatNumber}
          />
          <M3TextField
            label={t('label_fiscal_code')}
            value={form.fiscalCode ?? ''}
            placeholder={t('placeholder_fiscal_code')}
            onChange={handleChange('fiscalCode')}
            errorText={fieldErrors.fiscalCode}
          />
        </div>

        <M3TextField
          label={t('label_logo_url')}
          value={form.logoUrl ?? ''}
          placeholder={t('placeholder_logo_url')}
          onChange={handleChange('logoUrl')}
          errorText={fieldErrors.logoUrl}
        />

        <M3Checkbox
          className="pt-2 border-t border-outline-variant"
          checked={form.alloggiatiAutoSend}
          onChange={handleToggle}
          label={t('label_alloggiati_auto_send')}
          supportingText={t('hint_alloggiati_auto_send')}
        />
      </M3Card>

      <M3Card className="p-6 space-y-4">
        <div>
          <h2 className="text-base font-semibold text-on-surface">{t('section_title_alloggiati_credentials')}</h2>
          <p className="text-xs text-on-surface-variant mt-0.5">{t('hint_alloggiati_credentials')}</p>
        </div>

        <p
          className={`text-sm font-medium ${credentialsConfigured ? 'text-primary' : 'text-on-surface-variant'}`}
        >
          {credentialsConfigured
            ? t('status_alloggiati_credentials_configured')
            : t('status_alloggiati_credentials_not_configured')}
        </p>

        <M3TextField
          label={t('label_alloggiati_username')}
          value={form.alloggiatiUsername ?? ''}
          placeholder={t('placeholder_alloggiati_username')}
          onChange={handleChange('alloggiatiUsername')}
          autoComplete="off"
        />

        <div className="grid grid-cols-2 gap-4">
          <M3TextField
            label={t('label_alloggiati_password')}
            value={form.alloggiatiPassword ?? ''}
            placeholder={credentialsConfigured
              ? t('placeholder_alloggiati_credential_configured')
              : t('placeholder_alloggiati_credential_unconfigured')}
            onChange={handleChange('alloggiatiPassword')}
            type="password"
            autoComplete="new-password"
          />
          <M3TextField
            label={t('label_alloggiati_ws_key')}
            value={form.alloggiatiWsKey ?? ''}
            placeholder={credentialsConfigured
              ? t('placeholder_alloggiati_credential_configured')
              : t('placeholder_alloggiati_credential_unconfigured')}
            onChange={handleChange('alloggiatiWsKey')}
            type="password"
            autoComplete="new-password"
          />
        </div>
      </M3Card>

      <div className="flex justify-end">
        <M3Button icon="save" onClick={handleSave} disabled={saving}>
          {saving ? t('btn_saving') : t('btn_save_profile')}
        </M3Button>
      </div>
    </main>
  );
}
