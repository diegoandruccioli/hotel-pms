import { MaterialIcon } from './MaterialIcon';
import { useThemeStore } from '../store/themeStore';
import { useTranslation } from 'react-i18next';
import { useCallback } from 'react';

const themeOptions = [
  { value: 'light' as const, icon: 'light_mode', labelKey: 'theme_light' },
  { value: 'dark' as const, icon: 'dark_mode', labelKey: 'theme_dark' },
  { value: 'system' as const, icon: 'desktop_windows', labelKey: 'theme_system' },
] as const;

export const ThemeToggle = () => {
  const { t } = useTranslation('settings');
  const { theme, setTheme } = useThemeStore();

  const currentIndex = themeOptions.findIndex((o) => o.value === theme);
  const next = themeOptions[(currentIndex + 1) % themeOptions.length];

  const handleToggle = useCallback(() => {
    setTheme(next.value);
  }, [setTheme, next.value]);

  return (
    <button
      type="button"
      onClick={handleToggle}
      className="flex items-center justify-center w-10 h-10 rounded-shape-full text-on-surface-variant hover:bg-surface-container-highest transition-colors"
      title={t(next.labelKey)}
      aria-label={t('theme_toggle')}
    >
      <MaterialIcon
        name={themeOptions[currentIndex].icon}
        size={20}
      />
    </button>
  );
};
