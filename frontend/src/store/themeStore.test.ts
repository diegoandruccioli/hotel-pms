import { describe, it, expect, beforeEach, vi } from 'vitest';

// Mock matchMedia before importing themeStore
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

import { useThemeStore } from './themeStore';

describe('themeStore', () => {
  beforeEach(() => {
    useThemeStore.setState({ theme: 'system' });
    localStorage.clear();
    document.documentElement.classList.remove('dark');
  });

  it('should default to system theme', () => {
    expect(useThemeStore.getState().theme).toBe('system');
  });

  it('should set theme to dark', () => {
    useThemeStore.getState().setTheme('dark');
    expect(useThemeStore.getState().theme).toBe('dark');
  });

  it('should set theme to light', () => {
    useThemeStore.getState().setTheme('light');
    expect(useThemeStore.getState().theme).toBe('light');
  });

  it('should cycle between themes', () => {
    useThemeStore.getState().setTheme('light');
    expect(useThemeStore.getState().theme).toBe('light');

    useThemeStore.getState().setTheme('dark');
    expect(useThemeStore.getState().theme).toBe('dark');

    useThemeStore.getState().setTheme('system');
    expect(useThemeStore.getState().theme).toBe('system');
  });

  it('should persist theme to localStorage', () => {
    useThemeStore.getState().setTheme('dark');
    expect(localStorage.getItem('hotel-pms-theme')).toBe('dark');
  });

  it('treats "system" as dark when the OS prefers dark', () => {
    vi.mocked(window.matchMedia).mockReturnValueOnce({
      matches: true, media: '', onchange: null,
      addListener: vi.fn(), removeListener: vi.fn(),
      addEventListener: vi.fn(), removeEventListener: vi.fn(), dispatchEvent: vi.fn(),
    } as unknown as MediaQueryList);

    useThemeStore.getState().setTheme('system');

    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('reads a previously stored theme on module initialization', async () => {
    localStorage.setItem('hotel-pms-theme', 'dark');
    vi.resetModules();
    const { useThemeStore: freshStore } = await import('./themeStore');
    expect(freshStore.getState().theme).toBe('dark');
  });

  it('re-applies the theme via the matchMedia change listener when current theme is system', async () => {
    vi.resetModules();
    let changeHandler: (() => void) | undefined;
    vi.mocked(window.matchMedia).mockImplementation((query: string) => ({
      matches: true, media: query, onchange: null,
      addListener: vi.fn(), removeListener: vi.fn(),
      addEventListener: vi.fn((_event: string, handler: () => void) => { changeHandler = handler; }),
      removeEventListener: vi.fn(), dispatchEvent: vi.fn(),
    } as unknown as MediaQueryList));

    const { useThemeStore: freshStore } = await import('./themeStore');
    freshStore.setState({ theme: 'system' });
    document.documentElement.classList.remove('dark');

    changeHandler?.();

    expect(document.documentElement.classList.contains('dark')).toBe(true);
  });

  it('does not re-apply the theme via the change listener when current theme is not system', async () => {
    vi.resetModules();
    let changeHandler: (() => void) | undefined;
    vi.mocked(window.matchMedia).mockImplementation((query: string) => ({
      matches: true, media: query, onchange: null,
      addListener: vi.fn(), removeListener: vi.fn(),
      addEventListener: vi.fn((_event: string, handler: () => void) => { changeHandler = handler; }),
      removeEventListener: vi.fn(), dispatchEvent: vi.fn(),
    } as unknown as MediaQueryList));

    const { useThemeStore: freshStore } = await import('./themeStore');
    freshStore.setState({ theme: 'light' });
    document.documentElement.classList.remove('dark');

    changeHandler?.();

    expect(document.documentElement.classList.contains('dark')).toBe(false);
  });
});
