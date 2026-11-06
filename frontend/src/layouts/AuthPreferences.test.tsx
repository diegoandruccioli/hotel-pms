import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { AuthPreferences } from './AuthPreferences';
import { useSettingsStore } from '../store';

const i18nState = { language: 'it' };
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: i18nState }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../store/settingsStore', () => ({
  useSettingsStore: vi.fn(),
}));

const setContrast = vi.fn();
const setFontScale = vi.fn();
const setLanguage = vi.fn();

const withSettings = (contrast: 'normal' | 'high', fontScale: 'small' | 'normal' | 'large') =>
  vi.mocked(useSettingsStore).mockReturnValue({ contrast, fontScale, setContrast, setFontScale, setLanguage } as never);

describe('AuthPreferences', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    i18nState.language = 'it';
    withSettings('normal', 'normal');
  });

  it('is a named group of preference toggles', () => {
    render(<AuthPreferences />);
    expect(screen.getByRole('group', { name: 'prefs_label' })).toBeInTheDocument();
  });

  it('marks the active language and switches to the other on click', () => {
    render(<AuthPreferences />);
    expect(screen.getByRole('button', { name: 'lang_native_it' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'lang_native_en' })).toHaveAttribute('aria-pressed', 'false');

    fireEvent.click(screen.getByRole('button', { name: 'lang_native_en' }));
    expect(setLanguage).toHaveBeenCalledWith('en');
  });

  it('treats any non-Italian locale as English, like the settings page', () => {
    i18nState.language = 'de-DE';
    render(<AuthPreferences />);
    expect(screen.getByRole('button', { name: 'lang_native_en' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('names each language in its own language, so it can be found without reading the current one', () => {
    render(<AuthPreferences />);
    expect(screen.getByRole('button', { name: 'lang_native_it' })).toHaveAttribute('lang', 'it');
    expect(screen.getByRole('button', { name: 'lang_native_en' })).toHaveAttribute('lang', 'en');
  });

  it('toggles high contrast on and off', () => {
    const { unmount } = render(<AuthPreferences />);
    const chip = screen.getByRole('button', { name: 'pref_high_contrast' });
    expect(chip).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(chip);
    expect(setContrast).toHaveBeenCalledWith('high');
    unmount();

    withSettings('high', 'normal');
    render(<AuthPreferences />);
    expect(screen.getByRole('button', { name: 'pref_high_contrast' })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(screen.getByRole('button', { name: 'pref_high_contrast' }));
    expect(setContrast).toHaveBeenCalledWith('normal');
  });

  it('toggles large text between large and normal', () => {
    const { unmount } = render(<AuthPreferences />);
    fireEvent.click(screen.getByRole('button', { name: 'pref_large_text' }));
    expect(setFontScale).toHaveBeenCalledWith('large');
    unmount();

    withSettings('normal', 'large');
    render(<AuthPreferences />);
    expect(screen.getByRole('button', { name: 'pref_large_text' })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(screen.getByRole('button', { name: 'pref_large_text' }));
    expect(setFontScale).toHaveBeenCalledWith('normal');
  });

  it('has no accessibility violations', async () => {
    const { container } = render(<AuthPreferences />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
