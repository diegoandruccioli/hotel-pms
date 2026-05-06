import { useState, useEffect, useCallback, useRef, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { stayService } from '../services/stayService';
import type { HotelSettingsResponse, HotelSettingsRequest } from '../types/stay.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3Card } from '../components/m3/M3Card';
import { useToastStore } from '../store/toastStore';

// -----------------------------------------------------------------------
// ProfileField — reusable labelled input
// -----------------------------------------------------------------------

interface ProfileFieldProps {
  id: string;
  label: string;
  value: string;
  placeholder?: string;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  required?: boolean;
}

const ProfileField = memo(({ id, label, value, placeholder, onChange, required }: ProfileFieldProps) => (
  <div>
    <label htmlFor={id} className="block text-sm font-medium text-on-surface mb-1">
      {label}{required && <span aria-hidden="true"> *</span>}
    </label>
    <input
      id={id}
      type="text"
      value={value}
      placeholder={placeholder}
      onChange={onChange}
      className="w-full rounded-md border border-outline bg-surface px-3 py-2 text-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary"
    />
  </div>
));
ProfileField.displayName = 'ProfileField';

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
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const imgRef = useRef<HTMLImageElement>(null);
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
        });
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

  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      await stayService.updateHotelSettings(form);
      addToast(t('toast_profile_saved'), 'success');
    } catch {
      addToast(t('err_profile_save'), 'error');
    } finally {
      setSaving(false);
    }
  }, [form, addToast, t]);

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

        <ProfileField
          id="profile-hotel-name"
          label={t('label_hotel_name')}
          value={form.hotelName ?? ''}
          placeholder={t('placeholder_hotel_name')}
          onChange={handleChange('hotelName')}
        />

        <ProfileField
          id="profile-address"
          label={t('label_hotel_address')}
          value={form.address ?? ''}
          placeholder={t('placeholder_address')}
          onChange={handleChange('address')}
        />

        <div className="grid grid-cols-2 gap-4">
          <ProfileField
            id="profile-vat"
            label={t('label_vat_number')}
            value={form.vatNumber ?? ''}
            placeholder={t('placeholder_vat_number')}
            onChange={handleChange('vatNumber')}
          />
          <ProfileField
            id="profile-cf"
            label={t('label_fiscal_code')}
            value={form.fiscalCode ?? ''}
            placeholder={t('placeholder_fiscal_code')}
            onChange={handleChange('fiscalCode')}
          />
        </div>

        <ProfileField
          id="profile-logo"
          label={t('label_logo_url')}
          value={form.logoUrl ?? ''}
          placeholder={t('placeholder_logo_url')}
          onChange={handleChange('logoUrl')}
        />
      </M3Card>

      <div className="flex justify-end">
        <M3Button icon="save" onClick={handleSave} disabled={saving}>
          {saving ? t('btn_saving') : t('btn_save_profile')}
        </M3Button>
      </div>
    </main>
  );
}
