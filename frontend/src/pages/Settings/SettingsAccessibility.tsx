import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useSettingsStore, type FontScale } from '../../store/settingsStore';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Card } from '../../components/m3/M3Card';
import { M3SegmentedRow, type M3SegmentOption } from '../../components/m3/M3SegmentedRow';
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
          <button
            type="button"
            role="switch"
            aria-checked={contrast === 'high'}
            onClick={handleContrastToggle}
            className={[
              'flex items-center justify-between w-full',
              'px-4 py-3 rounded-[12px]',
              'border border-outline-variant',
              'hover:bg-surface-container-highest',
              'focus-visible:outline-none focus-visible:ring-2',
              'focus-visible:ring-primary focus-visible:ring-offset-2',
              'transition-colors',
            ].join(' ')}
          >
            <div className="flex items-center gap-3">
              <MaterialIcon name="contrast" size={20} className="text-on-surface-variant" />
              <div className="text-left">
                <p className="text-sm font-medium text-on-surface">{t('settings_high_contrast')}</p>
                <p className="text-xs text-on-surface-variant">{t('settings_high_contrast_desc')}</p>
              </div>
            </div>

            <div
              aria-hidden="true"
              className={[
                'relative w-12 h-7 rounded-shape-full border-2 transition-colors',
                contrast === 'high' ? 'bg-primary border-primary' : 'bg-surface-container-highest border-outline',
              ].join(' ')}
            >
              <span
                className={[
                  'absolute top-0.5 block w-5 h-5 rounded-shape-full',
                  'shadow-elevation-1 transition-all duration-200',
                  contrast === 'high' ? 'translate-x-[22px] bg-on-primary' : 'translate-x-0.5 bg-outline',
                ].join(' ')}
              />
            </div>
          </button>
        </section>
      </M3Card>
    </div>
  );
};
