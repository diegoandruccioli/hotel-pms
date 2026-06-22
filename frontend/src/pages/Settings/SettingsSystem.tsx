import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { stayService } from '../../services/stayService';
import type { HotelSettingsResponse } from '../../types/stay.types';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Card } from '../../components/m3/M3Card';
import { SettingsPageHeader } from '../../components/SettingsPageHeader';

export const SettingsSystem = () => {
  const { t } = useTranslation('settings');
  const navigate = useNavigate();

  const [hotelSettings, setHotelSettings] = useState<HotelSettingsResponse | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    stayService.getHotelSettings().then(setHotelSettings).catch(() => undefined);
  }, []);

  const handleBack = useCallback(() => navigate(-1), [navigate]);

  const handleAlloggiatiToggle = useCallback(async () => {
    if (!hotelSettings) return;
    const newValue = !hotelSettings.alloggiatiAutoSend;
    setSaving(true);
    try {
      const updated = await stayService.updateHotelSettings({ alloggiatiAutoSend: newValue });
      setHotelSettings(updated);
    } finally {
      setSaving(false);
    }
  }, [hotelSettings]);

  return (
    <div className="space-y-6 max-w-2xl mx-auto pb-10">
      <SettingsPageHeader icon="admin_panel_settings" title={t('settings_section_system')} onBack={handleBack} />

      <M3Card className="p-6">
        <button
          type="button"
          role="switch"
          aria-checked={hotelSettings?.alloggiatiAutoSend ?? false}
          onClick={handleAlloggiatiToggle}
          disabled={saving || hotelSettings === null}
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
            <MaterialIcon name="verified_user" size={20} className="text-on-surface-variant" />
            <div className="text-left">
              <p className="text-sm font-medium text-on-surface">{t('alloggiati_auto_send_label')}</p>
              <p className="text-xs text-on-surface-variant">{t('alloggiati_auto_send_desc')}</p>
            </div>
          </div>

          <div
            aria-hidden="true"
            className={[
              'relative w-12 h-7 rounded-shape-full border-2 transition-colors',
              hotelSettings?.alloggiatiAutoSend ? 'bg-primary border-primary' : 'bg-surface-container-highest border-outline',
            ].join(' ')}
          >
            <span
              className={[
                'absolute top-0.5 block w-5 h-5 rounded-shape-full',
                'shadow-elevation-1 transition-all duration-200',
                hotelSettings?.alloggiatiAutoSend ? 'translate-x-[22px] bg-on-primary' : 'translate-x-0.5 bg-outline',
              ].join(' ')}
            />
          </div>
        </button>
      </M3Card>
    </div>
  );
};
