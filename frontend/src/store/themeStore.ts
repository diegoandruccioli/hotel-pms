import { create } from 'zustand';

type Theme = 'light' | 'dark' | 'system';

interface ThemeState {
  theme: Theme;
  setTheme: (theme: Theme) => void;
}

const applyTheme = (theme: Theme) => {
  const root = document.documentElement;
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  const isDark = theme === 'dark' || (theme === 'system' && prefersDark);

  root.classList.toggle('dark', isDark);
  localStorage.setItem('hotel-pms-theme', theme);
};

const getInitialTheme = (): Theme => {
  if (typeof window === 'undefined') return 'system';
  const stored = localStorage.getItem('hotel-pms-theme') as Theme | null;
  return stored ?? 'system';
};

export const useThemeStore = create<ThemeState>((set) => {
  const initial = getInitialTheme();
  // Apply immediately on store creation
  applyTheme(initial);

  // Listen for system preference changes when theme is 'system'
  if (typeof window !== 'undefined') {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      const current = useThemeStore.getState().theme;
      if (current === 'system') applyTheme('system');
    });
  }

  return {
    theme: initial,
    setTheme: (theme) => {
      applyTheme(theme);
      set({ theme });
    },
  };
});
