import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { MemoryRouter } from 'react-router-dom';
import { SettingsAccessibility } from './SettingsAccessibility';
import { useSettingsStore } from '../../store/settingsStore';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const setContrast = vi.fn();
const setFontScale = vi.fn();
vi.mock('../../store/settingsStore', () => ({
  useSettingsStore: vi.fn(),
}));

const renderPage = () => render(<MemoryRouter><SettingsAccessibility /></MemoryRouter>);

describe('SettingsAccessibility', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useSettingsStore).mockReturnValue({
      contrast: 'normal', fontScale: 'normal', setContrast, setFontScale, setLanguage: vi.fn(),
    } as never);
  });

  it('navigates back in history when the back button is clicked', () => {
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'back' }));
    expect(mockNavigate).toHaveBeenCalledWith(-1);
  });

  it('renders the 3 font size options', () => {
    renderPage();
    expect(screen.getAllByRole('radio')).toHaveLength(3);
  });

  it('calls setFontScale when a font option is selected', () => {
    renderPage();
    fireEvent.click(screen.getByRole('radio', { name: 'font_large' }));
    expect(setFontScale).toHaveBeenCalledWith('large');
  });

  it('toggles high contrast from normal to high', () => {
    renderPage();
    fireEvent.click(screen.getByRole('switch'));
    expect(setContrast).toHaveBeenCalledWith('high');
  });

  it('toggles high contrast from high to normal', () => {
    vi.mocked(useSettingsStore).mockReturnValue({
      contrast: 'high', fontScale: 'normal', setContrast, setFontScale, setLanguage: vi.fn(),
    } as never);
    renderPage();
    fireEvent.click(screen.getByRole('switch'));
    expect(setContrast).toHaveBeenCalledWith('normal');
  });

  it('should have no accessibility violations', async () => {
    const { container } = renderPage();
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
