import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { MemoryRouter } from 'react-router-dom';
import { SettingsAppearance } from './SettingsAppearance';
import { useThemeStore } from '../../store/themeStore';
import { useSettingsStore } from '../../store/settingsStore';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'it' } }),
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
    vi.mocked(useThemeStore).mockReturnValue({ theme: 'system', setTheme } as never);
    vi.mocked(useSettingsStore).mockReturnValue({ setLanguage } as never);
  });

  it('navigates back in history when the back button is clicked', () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'back' }));
    expect(mockNavigate).toHaveBeenCalledWith(-1);
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

  it('should have no accessibility violations', async () => {
    const { container } = renderPage();
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
