import { useState, useEffect, useCallback, memo, type ChangeEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { stayService } from '../../services';
import type { HotelSettingsResponse, HotelSettingsRequest } from '../../types';
import { Alert } from '../../components/Alert';
import { M3Card } from '../../components/m3';
import { M3Switch } from '../../components/m3';
import { M3Textarea } from '../../components/m3';
import { M3TextField } from '../../components/m3';
import { SettingsSectionTitle } from '../../components/SettingsSectionTitle';

const EMAIL_GREETING_MAX_LENGTH = 300;

// -----------------------------------------------------------------------
// SubjectField — labelled text input for a per-email-type custom subject
// -----------------------------------------------------------------------

interface SubjectFieldProps {
  label: string;
  placeholder: string;
  value: string;
  onBlurSave: (value: string) => void;
}

const SubjectField = memo(({ label, placeholder, value, onBlurSave }: SubjectFieldProps) => {
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
    <M3TextField
      className="pl-4 pr-1"
      label={label}
      value={draft}
      placeholder={placeholder}
      onChange={handleChange}
      onBlur={handleBlur}
    />
  );
});
SubjectField.displayName = 'SubjectField';

// -----------------------------------------------------------------------
// SettingsSystem page
// -----------------------------------------------------------------------

export const SettingsSystem = () => {
  const { t } = useTranslation('settings');

  const [hotelSettings, setHotelSettings] = useState<HotelSettingsResponse | null>(null);
  const [saving, setSaving] = useState(false);
  const [greetingDraft, setGreetingDraft] = useState('');
  const [loadFailed, setLoadFailed] = useState(false);

  useEffect(() => {
    stayService.getHotelSettings().then((settings) => {
      setHotelSettings(settings);
      setGreetingDraft(settings.emailGreetingText ?? '');
    }).catch(() => setLoadFailed(true));
  }, []);

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
    <div className="space-y-6">
      <SettingsSectionTitle title={t('settings_section_system')} />

      {loadFailed && <Alert tone="error">{t('settings_system_load_failed')}</Alert>}

      <M3Card variant="solid" className="p-6">
        <M3Switch
          icon="verified_user"
          label={t('alloggiati_auto_send_label')}
          description={t('alloggiati_auto_send_desc')}
          checked={hotelSettings?.alloggiatiAutoSend ?? false}
          disabled={saving || hotelSettings === null}
          onChange={handleAlloggiatiToggle}
        />
      </M3Card>

      <M3Card variant="solid" className="p-6 space-y-4">
        <h2 className="text-sm font-semibold text-on-surface">{t('settings_section_email_notifications')}</h2>

        <div className="space-y-2">
          <M3Switch
            icon="mail"
            label={t('email_reservation_confirmed_label')}
            description={t('email_reservation_confirmed_desc')}
            checked={hotelSettings?.sendReservationConfirmedEmail ?? false}
            disabled={saving || hotelSettings === null}
            onChange={handleReservationEmailToggle}
          />
          {hotelSettings?.sendReservationConfirmedEmail && (
            <SubjectField
              label={t('email_subject_label')}
              placeholder={t('email_subject_placeholder')}
              value={hotelSettings.emailSubjectReservationConfirmed ?? ''}
              onBlurSave={handleReservationSubjectSave}
            />
          )}
        </div>

        <div className="space-y-2">
          <M3Switch
            icon="receipt_long"
            label={t('email_checkout_label')}
            description={t('email_checkout_desc')}
            checked={hotelSettings?.sendCheckoutEmail ?? false}
            disabled={saving || hotelSettings === null}
            onChange={handleCheckoutEmailToggle}
          />
          {hotelSettings?.sendCheckoutEmail && (
            <SubjectField
              label={t('email_subject_label')}
              placeholder={t('email_subject_placeholder')}
              value={hotelSettings.emailSubjectCheckout ?? ''}
              onBlurSave={handleCheckoutSubjectSave}
            />
          )}
        </div>

        <div className="pt-2 border-t border-outline-variant">
          <M3Textarea
            className="mt-3"
            label={t('email_greeting_label')}
            supportingText={t('email_greeting_desc')}
            value={greetingDraft}
            placeholder={t('email_greeting_placeholder')}
            maxLength={EMAIL_GREETING_MAX_LENGTH}
            rows={2}
            onChange={handleGreetingChange}
            onBlur={handleGreetingBlur}
          />
          <p className="text-xs text-on-surface-variant text-right mt-1">
            {greetingDraft.length}/{EMAIL_GREETING_MAX_LENGTH}
          </p>
        </div>
      </M3Card>
    </div>
  );
};
