import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

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

  describe('sidebarCollapsed', () => {
    // The initial value is computed when the store is created, so each case loads a fresh module.
    const originalInnerWidth = window.innerWidth;
    afterEach(() => {
      Object.defineProperty(window, 'innerWidth', { value: originalInnerWidth, configurable: true });
    });

    const loadStore = async (innerWidth: number, stored?: string) => {
      vi.resetModules();
      localStorage.clear();
      if (stored !== undefined) localStorage.setItem('hotel-pms-sidebar-collapsed', stored);
      Object.defineProperty(window, 'innerWidth', { value: innerWidth, configurable: true });
      return (await import('./settingsStore')).useSettingsStore;
    };

    it('starts compact below 1280px and expanded from 1280px when nothing is stored', async () => {
      expect((await loadStore(1100)).getState().sidebarCollapsed).toBe(true);
      expect((await loadStore(1279)).getState().sidebarCollapsed).toBe(true);
      expect((await loadStore(1280)).getState().sidebarCollapsed).toBe(false);
    });

    it('lets a stored choice win over the width default', async () => {
      expect((await loadStore(1100, 'false')).getState().sidebarCollapsed).toBe(false);
      expect((await loadStore(1600, 'true')).getState().sidebarCollapsed).toBe(true);
    });

    it('ignores a malformed stored value', async () => {
      expect((await loadStore(1100, 'banana')).getState().sidebarCollapsed).toBe(true);
    });

    it('persists set and toggle', async () => {
      const store = await loadStore(1600);
      store.getState().setSidebarCollapsed(true);
      expect(localStorage.getItem('hotel-pms-sidebar-collapsed')).toBe('true');
      store.getState().toggleSidebar();
      expect(store.getState().sidebarCollapsed).toBe(false);
      expect(localStorage.getItem('hotel-pms-sidebar-collapsed')).toBe('false');
    });
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
