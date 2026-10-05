import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useThemeStore } from '../../store';
import { useSettingsStore } from '../../store';
import { M3Card } from '../../components/m3';
import { M3SegmentedRow, type M3SegmentOption } from '../../components/m3';
import { SettingsSectionTitle } from '../../components/SettingsSectionTitle';

type ThemeValue = 'light' | 'dark' | 'system';

const THEME_OPTIONS: M3SegmentOption<ThemeValue>[] = [
  { value: 'light', labelKey: 'theme_light', icon: 'light_mode' },
  { value: 'dark', labelKey: 'theme_dark', icon: 'dark_mode' },
  { value: 'system', labelKey: 'theme_system', icon: 'desktop_windows' },
];

type LanguageValue = 'it' | 'en';

const LANGUAGE_OPTIONS: M3SegmentOption<LanguageValue>[] = [
  { value: 'it', labelKey: 'lang_italian' },
  { value: 'en', labelKey: 'lang_english' },
];

export const SettingsAppearance = () => {
  const { t, i18n } = useTranslation('settings');
  const { theme, setTheme } = useThemeStore();
  const { setLanguage } = useSettingsStore();

  const handleThemeChange = useCallback((v: ThemeValue) => setTheme(v), [setTheme]);
  const handleLanguageChange = useCallback((lang: LanguageValue) => setLanguage(lang), [setLanguage]);
  // i18n falls back to English for any locale other than Italian.
  const language: LanguageValue = i18n.language.startsWith('it') ? 'it' : 'en';

  return (
    <div className="space-y-6">
      <SettingsSectionTitle title={t('settings_appearance_language_title')} />

      <M3Card variant="solid" className="p-6 space-y-6">
        <section>
          <h2 className="text-xs font-semibold uppercase tracking-widest text-on-surface-variant mb-3">
            {t('settings_section_appearance')}
          </h2>
          <M3SegmentedRow<ThemeValue>
            options={THEME_OPTIONS}
            value={theme}
            onChange={handleThemeChange}
            ariaLabel={t('settings_theme_label')}
          />
        </section>

        <section>
          <h2 className="text-xs font-semibold uppercase tracking-widest text-on-surface-variant mb-3">
            {t('settings_section_language')}
          </h2>
          <M3SegmentedRow<LanguageValue>
            options={LANGUAGE_OPTIONS}
            value={language}
            onChange={handleLanguageChange}
            ariaLabel={t('settings_language_label')}
          />
        </section>
      </M3Card>
    </div>
  );
};
