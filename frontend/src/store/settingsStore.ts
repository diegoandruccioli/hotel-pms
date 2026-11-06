import { create } from 'zustand';

export type FontScale = 'small' | 'normal' | 'large';
export type ContrastMode = 'normal' | 'high';

const FONT_SCALE_MAP: Record<FontScale, string> = {
  small: '14px',
  normal: '16px',
  large: '18px',
};

const STORAGE_KEY_CONTRAST = 'hotel-pms-contrast';
const STORAGE_KEY_FONT = 'hotel-pms-font-scale';
const STORAGE_KEY_SIDEBAR = 'hotel-pms-sidebar-collapsed';

/** Viewports narrower than this start with the compact sidebar (until the user chooses). */
const SIDEBAR_AUTO_COLLAPSE_BELOW = 1280;

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

const applySidebarCollapsed = (collapsed: boolean) => {
  localStorage.setItem(STORAGE_KEY_SIDEBAR, String(collapsed));
};

// An explicit choice always wins; the width-based default only applies while
// there is none, and is evaluated once, at store creation (resizing never flips it).
const getInitialSidebarCollapsed = (): boolean => {
  if (typeof window === 'undefined') return false;
  const stored = localStorage.getItem(STORAGE_KEY_SIDEBAR);
  if (stored === 'true' || stored === 'false') return stored === 'true';
  return window.innerWidth < SIDEBAR_AUTO_COLLAPSE_BELOW;
};

interface SettingsState {
  contrast: ContrastMode;
  fontScale: FontScale;
  sidebarCollapsed: boolean;
  setContrast: (mode: ContrastMode) => void;
  setFontScale: (scale: FontScale) => void;
  setSidebarCollapsed: (collapsed: boolean) => void;
  toggleSidebar: () => void;
  setLanguage: (lang: string) => void;
}

export const useSettingsStore = create<SettingsState>(() => {
  const initialContrast = getInitialContrast();
  const initialFontScale = getInitialFontScale();
  const initialSidebarCollapsed = getInitialSidebarCollapsed();

  // Apply stored preferences immediately on store creation
  applyContrast(initialContrast);
  applyFontScale(initialFontScale);

  return {
    contrast: initialContrast,
    fontScale: initialFontScale,
    sidebarCollapsed: initialSidebarCollapsed,
    setContrast: (mode) => {
      applyContrast(mode);
      useSettingsStore.setState({ contrast: mode });
    },
    setFontScale: (scale) => {
      applyFontScale(scale);
      useSettingsStore.setState({ fontScale: scale });
    },
    setSidebarCollapsed: (collapsed) => {
      applySidebarCollapsed(collapsed);
      useSettingsStore.setState({ sidebarCollapsed: collapsed });
    },
    toggleSidebar: () => {
      const { sidebarCollapsed, setSidebarCollapsed } = useSettingsStore.getState();
      setSidebarCollapsed(!sidebarCollapsed);
    },
    setLanguage: (lang) => {
      // Dynamic import, not a static one: `../i18n` runs i18n.init() as a
      // module-load side effect, which must NOT fire just because something
      // imported an unrelated member of the store/ barrel (which re-exports
      // this module too). Deferring the import to call time means the real
      // i18n singleton only loads when a language change is actually
      // requested, not whenever any store is touched.
      void import('../i18n').then(({ default: i18n }) => i18n.changeLanguage(lang));
    },
  };
});
