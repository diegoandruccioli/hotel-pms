import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { MemoryRouter } from 'react-router-dom';
import { SettingsAppearance } from './SettingsAppearance';
import { useThemeStore } from '../../store';
import { useSettingsStore } from '../../store';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

const i18nState = vi.hoisted(() => ({ language: 'it' }));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: i18nState }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const setTheme = vi.fn();
const setLanguage = vi.fn();
vi.mock('../../store/themeStore', () => ({ useThemeStore: vi.fn() }));
vi.mock('../../store/settingsStore', () => ({ useSettingsStore: vi.fn() }));

const renderPage = () => render(<MemoryRouter><SettingsAppearance /></MemoryRouter>);

describe('SettingsAppearance', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    i18nState.language = 'it';
    vi.mocked(useThemeStore).mockReturnValue({ theme: 'system', setTheme } as never);
    vi.mocked(useSettingsStore).mockReturnValue({ setLanguage } as never);
  });

  it('has no back button of its own: the settings layout provides the navigation', () => {
    renderPage();
    expect(screen.queryByRole('button', { name: 'back' })).not.toBeInTheDocument();
  });

  it('renders the 3 theme options and 2 language options', () => {
    renderPage();
    expect(screen.getAllByRole('radio')).toHaveLength(5);
  });

  it('calls setTheme when a theme option is selected', () => {
    renderPage();
    fireEvent.click(screen.getByRole('radio', { name: 'theme_dark' }));
    expect(setTheme).toHaveBeenCalledWith('dark');
  });

  it('marks the active language as checked and calls setLanguage on selection', () => {
    renderPage();
    expect(screen.getByRole('radio', { name: /lang_italian/ })).toHaveAttribute('aria-checked', 'true');
    fireEvent.click(screen.getByRole('radio', { name: /lang_english/ }));
    expect(setLanguage).toHaveBeenCalledWith('en');
  });

  it('marks English as checked for an English locale such as en-GB', () => {
    i18nState.language = 'en-GB';
    renderPage();
    expect(screen.getByRole('radio', { name: /lang_english/ })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('radio', { name: /lang_italian/ })).toHaveAttribute('aria-checked', 'false');
  });

  it('marks English as checked for an unsupported locale, since i18n falls back to English', () => {
    i18nState.language = 'de-DE';
    renderPage();
    expect(screen.getByRole('radio', { name: /lang_english/ })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('radio', { name: /lang_italian/ })).toHaveAttribute('aria-checked', 'false');
  });

  it('should have no accessibility violations', async () => {
    const { container } = renderPage();
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
