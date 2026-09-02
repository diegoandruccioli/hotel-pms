import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useSettingsStore, type FontScale } from '../../store/settingsStore';
import { M3Card } from '../../components/m3';
import { M3SegmentedRow, type M3SegmentOption } from '../../components/m3';
import { M3Switch } from '../../components/m3';
import { SettingsPageHeader } from '../../components/SettingsPageHeader';

const FONT_OPTIONS: M3SegmentOption<FontScale>[] = [
  { value: 'small', labelKey: 'font_small', icon: 'text_fields' },
  { value: 'normal', labelKey: 'font_normal', icon: 'text_fields' },
  { value: 'large', labelKey: 'font_large', icon: 'text_fields' },
];

export const SettingsAccessibility = () => {
  const { t } = useTranslation('settings');
  const navigate = useNavigate();
  const { contrast, fontScale, setContrast, setFontScale } = useSettingsStore();

  const handleBack = useCallback(() => navigate(-1), [navigate]);
  const handleFontChange = useCallback((v: FontScale) => setFontScale(v), [setFontScale]);
  const handleContrastToggle = useCallback(() => {
    setContrast(contrast === 'high' ? 'normal' : 'high');
  }, [contrast, setContrast]);

  return (
    <div className="space-y-6 max-w-2xl mx-auto pb-10">
      <SettingsPageHeader icon="accessibility_new" title={t('settings_section_accessibility')} onBack={handleBack} />

      <M3Card className="p-6 space-y-6">
        <section>
          <h2 className="text-xs font-semibold uppercase tracking-widest text-on-surface-variant mb-3">
            {t('settings_section_typography')}
          </h2>
          <M3SegmentedRow<FontScale>
            options={FONT_OPTIONS}
            value={fontScale}
            onChange={handleFontChange}
            ariaLabel={t('settings_font_label')}
          />
        </section>

        <section>
          <M3Switch
            checked={contrast === 'high'}
            onChange={handleContrastToggle}
            icon="contrast"
            label={t('settings_high_contrast')}
            description={t('settings_high_contrast_desc')}
          />
        </section>
      </M3Card>
    </div>
  );
};
