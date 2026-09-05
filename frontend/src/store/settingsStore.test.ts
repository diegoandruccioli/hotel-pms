import { describe, it, expect, vi, beforeEach } from 'vitest';

const changeLanguageMock = vi.fn();

vi.mock('../i18n', () => ({
  default: { changeLanguage: changeLanguageMock },
}));

import { useSettingsStore } from './settingsStore';

describe('settingsStore', () => {
  beforeEach(() => {
    changeLanguageMock.mockClear();
    localStorage.clear();
    document.documentElement.removeAttribute('data-contrast');
    document.documentElement.style.removeProperty('--md-font-scale');
  });

  it('applies high contrast and persists it', () => {
    useSettingsStore.getState().setContrast('high');

    expect(document.documentElement.getAttribute('data-contrast')).toBe('high');
    expect(localStorage.getItem('hotel-pms-contrast')).toBe('high');
    expect(useSettingsStore.getState().contrast).toBe('high');
  });

  it('reverts to normal contrast and removes the attribute', () => {
    useSettingsStore.getState().setContrast('high');
    useSettingsStore.getState().setContrast('normal');

    expect(document.documentElement.hasAttribute('data-contrast')).toBe(false);
    expect(localStorage.getItem('hotel-pms-contrast')).toBe('normal');
  });

  it('applies font scale and persists it', () => {
    useSettingsStore.getState().setFontScale('large');

    expect(document.documentElement.style.getPropertyValue('--md-font-scale')).toBe('18px');
    expect(localStorage.getItem('hotel-pms-font-scale')).toBe('large');
    expect(useSettingsStore.getState().fontScale).toBe('large');
  });

  it('defers to a dynamically imported i18n instance to change language', async () => {
    useSettingsStore.getState().setLanguage('it');

    await vi.waitFor(() => expect(changeLanguageMock).toHaveBeenCalledWith('it'));
  });
});
