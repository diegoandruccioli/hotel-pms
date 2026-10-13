import { memo, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { M3FilterChip } from '../components/m3';
import { useSettingsStore } from '../store';

type LanguageValue = 'it' | 'en';

/** Language, contrast and text size, reachable before signing in: the people who need them most
 * are the ones who cannot read the login form without them. */
export const AuthPreferences = memo(() => {
  const { t, i18n } = useTranslation('auth');
  const { contrast, fontScale, setContrast, setFontScale, setLanguage } = useSettingsStore();

  // i18n falls back to English for any locale other than Italian (as in the settings page).
  const language: LanguageValue = i18n.language.startsWith('it') ? 'it' : 'en';

  const handleLanguage = useCallback((lang: LanguageValue) => setLanguage(lang), [setLanguage]);
  const handleContrast = useCallback(
    () => setContrast(contrast === 'high' ? 'normal' : 'high'),
    [contrast, setContrast],
  );
  const handleFontScale = useCallback(
    () => setFontScale(fontScale === 'large' ? 'normal' : 'large'),
    [fontScale, setFontScale],
  );

  return (
    <div role="group" aria-label={t('prefs_label')} className="mt-6 flex flex-wrap justify-center gap-2">
      <M3FilterChip<LanguageValue>
        value="it"
        selected={language === 'it'}
        label={t('lang_native_it')}
        lang="it"
        onValueSelect={handleLanguage}
      />
      <M3FilterChip<LanguageValue>
        value="en"
        selected={language === 'en'}
        label={t('lang_native_en')}
        lang="en"
        onValueSelect={handleLanguage}
      />
      <M3FilterChip selected={contrast === 'high'} label={t('pref_high_contrast')} onClick={handleContrast} />
      <M3FilterChip selected={fontScale === 'large'} label={t('pref_large_text')} onClick={handleFontScale} />
    </div>
  );
});

AuthPreferences.displayName = 'AuthPreferences';
