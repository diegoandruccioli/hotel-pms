import { create } from 'zustand';
import i18n from '../i18n';

export type FontScale = 'small' | 'normal' | 'large';
export type ContrastMode = 'normal' | 'high';

const FONT_SCALE_MAP: Record<FontScale, string> = {
  small: '14px',
  normal: '16px',
  large: '18px',
};

const STORAGE_KEY_CONTRAST = 'hotel-pms-contrast';
const STORAGE_KEY_FONT = 'hotel-pms-font-scale';

const applyContrast = (mode: ContrastMode) => {
  const root = document.documentElement;
  if (mode === 'high') {
    root.setAttribute('data-contrast', 'high');
  } else {
    root.removeAttribute('data-contrast');
  }
  localStorage.setItem(STORAGE_KEY_CONTRAST, mode);
};

const applyFontScale = (scale: FontScale) => {
  document.documentElement.style.setProperty(
    '--md-font-scale',
    FONT_SCALE_MAP[scale]
  );
  localStorage.setItem(STORAGE_KEY_FONT, scale);
};

const getInitialContrast = (): ContrastMode => {
  if (typeof window === 'undefined') return 'normal';
  return (localStorage.getItem(STORAGE_KEY_CONTRAST) as ContrastMode) ?? 'normal';
};

const getInitialFontScale = (): FontScale => {
  if (typeof window === 'undefined') return 'normal';
  return (localStorage.getItem(STORAGE_KEY_FONT) as FontScale) ?? 'normal';
};

interface SettingsState {
  contrast: ContrastMode;
  fontScale: FontScale;
  setContrast: (mode: ContrastMode) => void;
  setFontScale: (scale: FontScale) => void;
  setLanguage: (lang: string) => void;
}

export const useSettingsStore = create<SettingsState>(() => {
  const initialContrast = getInitialContrast();
  const initialFontScale = getInitialFontScale();

  // Apply stored preferences immediately on store creation
  applyContrast(initialContrast);
  applyFontScale(initialFontScale);

  return {
    contrast: initialContrast,
    fontScale: initialFontScale,
    setContrast: (mode) => {
      applyContrast(mode);
      useSettingsStore.setState({ contrast: mode });
    },
    setFontScale: (scale) => {
      applyFontScale(scale);
      useSettingsStore.setState({ fontScale: scale });
    },
    setLanguage: (lang) => {
      i18n.changeLanguage(lang);
    },
  };
});
