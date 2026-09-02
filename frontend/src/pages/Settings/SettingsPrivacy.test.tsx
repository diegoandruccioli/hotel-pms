import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { SettingsPrivacy } from './SettingsPrivacy';
import { guestService } from '../../services/guestService';
import { mockAxiosErrorWithDetail } from '../../test-utils';
import type { GuestPrivacySettingsResponse } from '../../types';

const stableT = (key: string, opts?: Record<string, unknown>) =>
  opts ? `${key}:${JSON.stringify(opts)}` : key;
const stableI18n = { language: 'en' };
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: stableT, i18n: stableI18n }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/guestService');
const mockAddToast = vi.fn();
vi.mock('../../store/toastStore', () => ({
  useToastStore: (sel: unknown) =>
    (sel as (s: { addToast: () => void }) => unknown)({ addToast: mockAddToast }),
}));

const SETTINGS: GuestPrivacySettingsResponse = {
  hotelId: 'h1', guestRetentionYears: 5, tulpsMinYears: 5, fiscalMinYears: 10,
};

const renderPage = () =>
  render(
    <MemoryRouter>
      <SettingsPrivacy />
    </MemoryRouter>,
  );

describe('SettingsPrivacy', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('renders the page title', async () => {
    vi.mocked(guestService.getPrivacySettings).mockResolvedValue(SETTINGS);
    renderPage();
    expect(screen.getByText('settings_section_privacy')).toBeInTheDocument();
    await waitFor(() => expect(guestService.getPrivacySettings).toHaveBeenCalled());
  });

  it('loads and displays the current retention settings', async () => {
    vi.mocked(guestService.getPrivacySettings).mockResolvedValue(SETTINGS);
    renderPage();

    await waitFor(() => expect(screen.getByDisplayValue('5')).toBeInTheDocument());
    expect(screen.getByText(/privacy_years_value.*"count":5/)).toBeInTheDocument();
    expect(screen.getByText(/privacy_years_value.*"count":10/)).toBeInTheDocument();
  });

  it('shows an error toast when loading settings fails', async () => {
    vi.mocked(guestService.getPrivacySettings)
      .mockRejectedValue(mockAxiosErrorWithDetail('INTERNAL_SERVER_ERROR', 500));
    renderPage();

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('INTERNAL_SERVER_ERROR', 'error'));
  });

  it('rejects a retention value below the TULPS minimum before calling the API', async () => {
    vi.mocked(guestService.getPrivacySettings).mockResolvedValue(SETTINGS);
    renderPage();
    await waitFor(() => expect(screen.getByDisplayValue('5')).toBeInTheDocument());

    fireEvent.change(screen.getByDisplayValue('5'), { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: 'save' }));

    await waitFor(() => expect(screen.getByText(/privacy_err_min_years/)).toBeInTheDocument());
    expect(guestService.updatePrivacySettings).not.toHaveBeenCalled();
  });

  it('submits a valid retention value and shows a success toast', async () => {
    vi.mocked(guestService.getPrivacySettings).mockResolvedValue(SETTINGS);
    vi.mocked(guestService.updatePrivacySettings).mockResolvedValue({ ...SETTINGS, guestRetentionYears: 7 });
    renderPage();
    await waitFor(() => expect(screen.getByDisplayValue('5')).toBeInTheDocument());

    fireEvent.change(screen.getByDisplayValue('5'), { target: { value: '7' } });
    fireEvent.click(screen.getByRole('button', { name: 'save' }));

    await waitFor(() => expect(guestService.updatePrivacySettings)
      .toHaveBeenCalledWith({ guestRetentionYears: 7 }));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('save', 'success'));
  });

  it('should have no accessibility violations', async () => {
    vi.mocked(guestService.getPrivacySettings).mockResolvedValue(SETTINGS);
    const { container } = renderPage();
    await waitFor(() => expect(screen.getByDisplayValue('5')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
