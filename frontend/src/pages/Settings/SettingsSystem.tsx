import { useState, useEffect, useCallback, memo, type ChangeEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { stayService } from '../../services/stayService';
import type { HotelSettingsResponse, HotelSettingsRequest } from '../../types/stay.types';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Card } from '../../components/m3/M3Card';
import { SettingsPageHeader } from '../../components/SettingsPageHeader';

const EMAIL_GREETING_MAX_LENGTH = 300;

// -----------------------------------------------------------------------
// ToggleRow — reusable switch row (Alloggiati auto-send + email toggles)
// -----------------------------------------------------------------------

interface ToggleRowProps {
  icon: string;
  label: string;
  description: string;
  checked: boolean;
  disabled: boolean;
  onToggle: () => void;
}

const ToggleRow = memo(({ icon, label, description, checked, disabled, onToggle }: ToggleRowProps) => (
  <button
    type="button"
    role="switch"
    aria-checked={checked}
    onClick={onToggle}
    disabled={disabled}
    className={[
      'flex items-center justify-between w-full',
      'px-4 py-3 rounded-[12px]',
      'border border-outline-variant',
      'hover:bg-surface-container-highest',
      'focus-visible:outline-none focus-visible:ring-2',
      'focus-visible:ring-primary focus-visible:ring-offset-2',
      'transition-colors disabled:opacity-50',
    ].join(' ')}
  >
    <div className="flex items-center gap-3">
      <MaterialIcon name={icon} size={20} className="text-on-surface-variant" />
      <div className="text-left">
        <p className="text-sm font-medium text-on-surface">{label}</p>
        <p className="text-xs text-on-surface-variant">{description}</p>
      </div>
    </div>

    <div
      aria-hidden="true"
      className={[
        'relative w-12 h-7 rounded-shape-full border-2 transition-colors shrink-0',
        checked ? 'bg-primary border-primary' : 'bg-surface-container-highest border-outline',
      ].join(' ')}
    >
      <span
        className={[
          'absolute top-0.5 block w-5 h-5 rounded-shape-full',
          'shadow-elevation-1 transition-all duration-200',
          checked ? 'translate-x-[22px] bg-on-primary' : 'translate-x-0.5 bg-outline',
        ].join(' ')}
      />
    </div>
  </button>
));
ToggleRow.displayName = 'ToggleRow';

// -----------------------------------------------------------------------
// SubjectField — labelled text input for a per-email-type custom subject
// -----------------------------------------------------------------------

interface SubjectFieldProps {
  id: string;
  label: string;
  placeholder: string;
  value: string;
  onBlurSave: (value: string) => void;
}

const SubjectField = memo(({ id, label, placeholder, value, onBlurSave }: SubjectFieldProps) => {
  const [draft, setDraft] = useState(value);
  // Reset the local draft when the saved value changes (e.g. after a successful save
  // elsewhere), without a useEffect — adjusting state during render per React docs.
  const [lastSyncedValue, setLastSyncedValue] = useState(value);
  if (value !== lastSyncedValue) {
    setLastSyncedValue(value);
    setDraft(value);
  }

  const handleChange = useCallback((e: ChangeEvent<HTMLInputElement>) => setDraft(e.target.value), []);
  const handleBlur = useCallback(() => {
    if (draft !== value) onBlurSave(draft);
  }, [draft, value, onBlurSave]);

  return (
    <div className="pl-4 pr-1">
      <label htmlFor={id} className="block text-xs font-medium text-on-surface-variant mb-1">
        {label}
      </label>
      <input
        id={id}
        type="text"
        value={draft}
        placeholder={placeholder}
        onChange={handleChange}
        onBlur={handleBlur}
        className="w-full rounded-md border border-outline bg-surface px-3 py-2 text-sm text-on-surface
          focus:outline-none focus:ring-2 focus:ring-primary"
      />
    </div>
  );
});
SubjectField.displayName = 'SubjectField';

// -----------------------------------------------------------------------
// SettingsSystem page
// -----------------------------------------------------------------------

export const SettingsSystem = () => {
  const { t } = useTranslation('settings');
  const navigate = useNavigate();

  const [hotelSettings, setHotelSettings] = useState<HotelSettingsResponse | null>(null);
  const [saving, setSaving] = useState(false);
  const [greetingDraft, setGreetingDraft] = useState('');

  useEffect(() => {
    stayService.getHotelSettings().then((settings) => {
      setHotelSettings(settings);
      setGreetingDraft(settings.emailGreetingText ?? '');
    }).catch(() => undefined);
  }, []);

  const handleBack = useCallback(() => navigate(-1), [navigate]);

  const patch = useCallback(async (partial: HotelSettingsRequest) => {
    setSaving(true);
    try {
      const updated = await stayService.updateHotelSettings(partial);
      setHotelSettings(updated);
      return updated;
    } finally {
      setSaving(false);
    }
  }, []);

  const handleAlloggiatiToggle = useCallback(async () => {
    if (!hotelSettings) return;
    await patch({ alloggiatiAutoSend: !hotelSettings.alloggiatiAutoSend });
  }, [hotelSettings, patch]);

  const handleReservationEmailToggle = useCallback(async () => {
    if (!hotelSettings) return;
    await patch({ sendReservationConfirmedEmail: !hotelSettings.sendReservationConfirmedEmail });
  }, [hotelSettings, patch]);

  const handleCheckoutEmailToggle = useCallback(async () => {
    if (!hotelSettings) return;
    await patch({ sendCheckoutEmail: !hotelSettings.sendCheckoutEmail });
  }, [hotelSettings, patch]);

  const handleReservationSubjectSave = useCallback((value: string) => {
    void patch({ emailSubjectReservationConfirmed: value });
  }, [patch]);

  const handleCheckoutSubjectSave = useCallback((value: string) => {
    void patch({ emailSubjectCheckout: value });
  }, [patch]);

  const handleGreetingBlur = useCallback(() => {
    if (greetingDraft !== (hotelSettings?.emailGreetingText ?? '')) {
      void patch({ emailGreetingText: greetingDraft });
    }
  }, [greetingDraft, hotelSettings, patch]);

  const handleGreetingChange = useCallback(
    (e: ChangeEvent<HTMLTextAreaElement>) => setGreetingDraft(e.target.value),
    [],
  );

  return (
    <div className="space-y-6 max-w-2xl mx-auto pb-10">
      <SettingsPageHeader icon="admin_panel_settings" title={t('settings_section_system')} onBack={handleBack} />

      <M3Card className="p-6">
        <ToggleRow
          icon="verified_user"
          label={t('alloggiati_auto_send_label')}
          description={t('alloggiati_auto_send_desc')}
          checked={hotelSettings?.alloggiatiAutoSend ?? false}
          disabled={saving || hotelSettings === null}
          onToggle={handleAlloggiatiToggle}
        />
      </M3Card>

      <M3Card className="p-6 space-y-4">
        <h2 className="text-sm font-semibold text-on-surface">{t('settings_section_email_notifications')}</h2>

        <div className="space-y-2">
          <ToggleRow
            icon="mail"
            label={t('email_reservation_confirmed_label')}
            description={t('email_reservation_confirmed_desc')}
            checked={hotelSettings?.sendReservationConfirmedEmail ?? false}
            disabled={saving || hotelSettings === null}
            onToggle={handleReservationEmailToggle}
          />
          {hotelSettings?.sendReservationConfirmedEmail && (
            <SubjectField
              id="email-subject-reservation-confirmed"
              label={t('email_subject_label')}
              placeholder={t('email_subject_placeholder')}
              value={hotelSettings.emailSubjectReservationConfirmed ?? ''}
              onBlurSave={handleReservationSubjectSave}
            />
          )}
        </div>

        <div className="space-y-2">
          <ToggleRow
            icon="receipt_long"
            label={t('email_checkout_label')}
            description={t('email_checkout_desc')}
            checked={hotelSettings?.sendCheckoutEmail ?? false}
            disabled={saving || hotelSettings === null}
            onToggle={handleCheckoutEmailToggle}
          />
          {hotelSettings?.sendCheckoutEmail && (
            <SubjectField
              id="email-subject-checkout"
              label={t('email_subject_label')}
              placeholder={t('email_subject_placeholder')}
              value={hotelSettings.emailSubjectCheckout ?? ''}
              onBlurSave={handleCheckoutSubjectSave}
            />
          )}
        </div>

        <div className="pt-2 border-t border-outline-variant">
          <label htmlFor="email-greeting-text" className="block text-sm font-medium text-on-surface mb-1 mt-3">
            {t('email_greeting_label')}
          </label>
          <p className="text-xs text-on-surface-variant mb-2">{t('email_greeting_desc')}</p>
          <textarea
            id="email-greeting-text"
            value={greetingDraft}
            placeholder={t('email_greeting_placeholder')}
            maxLength={EMAIL_GREETING_MAX_LENGTH}
            rows={2}
            onChange={handleGreetingChange}
            onBlur={handleGreetingBlur}
            className="w-full rounded-md border border-outline bg-surface px-3 py-2 text-sm text-on-surface
              focus:outline-none focus:ring-2 focus:ring-primary resize-none"
          />
          <p className="text-xs text-on-surface-variant text-right mt-1">
            {greetingDraft.length}/{EMAIL_GREETING_MAX_LENGTH}
          </p>
        </div>
      </M3Card>
    </div>
  );
};
