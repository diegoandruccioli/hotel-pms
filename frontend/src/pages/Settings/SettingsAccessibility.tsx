import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useSettingsStore, type FontScale } from '../../store';
import { M3Card } from '../../components/m3';
import { M3SegmentedRow, type M3SegmentOption } from '../../components/m3';
import { M3Switch } from '../../components/m3';
import { SettingsSectionTitle } from '../../components/SettingsSectionTitle';

const FONT_OPTIONS: M3SegmentOption<FontScale>[] = [
  { value: 'small', labelKey: 'font_small', icon: 'text_fields' },
  { value: 'normal', labelKey: 'font_normal', icon: 'text_fields' },
  { value: 'large', labelKey: 'font_large', icon: 'text_fields' },
];

export const SettingsAccessibility = () => {
  const { t } = useTranslation('settings');
  const { contrast, fontScale, setContrast, setFontScale } = useSettingsStore();

  const handleFontChange = useCallback((v: FontScale) => setFontScale(v), [setFontScale]);
  const handleContrastToggle = useCallback(() => {
    setContrast(contrast === 'high' ? 'normal' : 'high');
  }, [contrast, setContrast]);

  return (
    <div className="space-y-6">
      <SettingsSectionTitle title={t('settings_section_accessibility')} />

      <M3Card variant="solid" className="p-6 space-y-6">
        <section>
          <h3 className="text-xs font-semibold uppercase tracking-widest text-on-surface-variant mb-3">
            {t('settings_section_typography')}
          </h3>
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
