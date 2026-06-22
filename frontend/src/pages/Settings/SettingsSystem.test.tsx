import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { MemoryRouter } from 'react-router-dom';
import { SettingsSystem } from './SettingsSystem';
import { stayService } from '../../services/stayService';
import type { HotelSettingsResponse } from '../../types/stay.types';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/stayService', () => ({
  stayService: { getHotelSettings: vi.fn(), updateHotelSettings: vi.fn() },
}));

const SETTINGS: HotelSettingsResponse = {
  hotelId: 'h-1', alloggiatiAutoSend: false, alloggiatiCredentialsConfigured: false,
};

const renderPage = () => render(<MemoryRouter><SettingsSystem /></MemoryRouter>);

describe('SettingsSystem', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('navigates to the settings hub when the back button is clicked', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue(SETTINGS);
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'back' }));
    expect(mockNavigate).toHaveBeenCalledWith('/settings');
  });

  it('loads hotel settings and reflects the current alloggiatiAutoSend value', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue(SETTINGS);
    renderPage();
    await waitFor(() => expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'false'));
  });

  it('disables the toggle while settings are still loading', () => {
    vi.mocked(stayService.getHotelSettings).mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(screen.getByRole('switch')).toBeDisabled();
  });

  it('toggles alloggiatiAutoSend on click', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue(SETTINGS);
    vi.mocked(stayService.updateHotelSettings).mockResolvedValue({ ...SETTINGS, alloggiatiAutoSend: true });
    renderPage();
    await waitFor(() => expect(screen.getByRole('switch')).not.toBeDisabled());

    fireEvent.click(screen.getByRole('switch'));

    await waitFor(() => expect(stayService.updateHotelSettings).toHaveBeenCalledWith({ alloggiatiAutoSend: true }));
    await waitFor(() => expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'true'));
  });

  it('should have no accessibility violations', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue(SETTINGS);
    const { container } = renderPage();
    await waitFor(() => expect(screen.getByRole('switch')).not.toBeDisabled());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
