import { useCallback, memo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useThemeStore } from '../../store/themeStore';
import { useSettingsStore } from '../../store/settingsStore';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Card } from '../../components/m3/M3Card';
import { M3SegmentedRow, type M3SegmentOption } from '../../components/m3/M3SegmentedRow';
import { SettingsPageHeader } from '../../components/SettingsPageHeader';

type ThemeValue = 'light' | 'dark' | 'system';

const THEME_OPTIONS: M3SegmentOption<ThemeValue>[] = [
  { value: 'light', labelKey: 'theme_light', icon: 'light_mode' },
  { value: 'dark', labelKey: 'theme_dark', icon: 'dark_mode' },
  { value: 'system', labelKey: 'theme_system', icon: 'desktop_windows' },
];

interface LangOption {
  value: string;
  labelKey: string;
  flag: string;
}

const LANGUAGE_OPTIONS: LangOption[] = [
  { value: 'it', labelKey: 'lang_italian', flag: '🇮🇹' },
  { value: 'en', labelKey: 'lang_english', flag: '🇬🇧' },
];

const LanguageButton = memo(({
  lang,
  isActive,
  onSelect,
}: {
  lang: LangOption;
  isActive: boolean;
  onSelect: (v: string) => void;
}) => {
  const { t } = useTranslation('settings');
  const handleClick = useCallback(() => onSelect(lang.value), [onSelect, lang.value]);

  return (
    <button
      type="button"
      role="radio"
      aria-checked={isActive}
      onClick={handleClick}
      className={[
        'flex items-center gap-3 px-4 py-3 rounded-[12px]',
        'text-sm font-body text-left',
        'border transition-colors',
        'focus-visible:outline-none focus-visible:ring-2',
        'focus-visible:ring-primary focus-visible:ring-offset-2',
        isActive
          ? 'bg-primary-container text-on-primary-container border-primary'
          : 'border-outline-variant text-on-surface hover:bg-surface-container-highest',
      ].join(' ')}
    >
      <span className="text-xl leading-none" aria-hidden="true">{lang.flag}</span>
      <span className="flex-1">{t(lang.labelKey)}</span>
      {isActive && <MaterialIcon name="check_circle" size={18} filled className="text-primary shrink-0" />}
    </button>
  );
});
LanguageButton.displayName = 'LanguageButton';

export const SettingsAppearance = () => {
  const { t, i18n } = useTranslation('settings');
  const navigate = useNavigate();
  const { theme, setTheme } = useThemeStore();
  const { setLanguage } = useSettingsStore();

  const handleBack = useCallback(() => navigate(-1), [navigate]);
  const handleThemeChange = useCallback((v: ThemeValue) => setTheme(v), [setTheme]);
  const handleLanguageChange = useCallback((lang: string) => setLanguage(lang), [setLanguage]);

  return (
    <div className="space-y-6 max-w-2xl mx-auto pb-10">
      <SettingsPageHeader icon="palette" title={t('settings_appearance_language_title')} onBack={handleBack} />

      <M3Card className="p-6 space-y-6">
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
          <div role="radiogroup" aria-label={t('settings_language_label')} className="flex flex-col gap-2">
            {LANGUAGE_OPTIONS.map((lang) => (
              <LanguageButton
                key={lang.value}
                lang={lang}
                isActive={i18n.language.startsWith(lang.value)}
                onSelect={handleLanguageChange}
              />
            ))}
          </div>
        </section>
      </M3Card>
    </div>
  );
};
