import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { MemoryRouter } from 'react-router-dom';
import { SettingsSystem } from './SettingsSystem';
import { stayService } from '../../services';
import type { HotelSettingsResponse } from '../../types';
import { mockAxiosErrorWithDetail } from '../../test-utils/mockAxiosError';

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

const mockAddToast = vi.fn();
vi.mock('../../store/toastStore', () => ({
  useToastStore: (sel: unknown) =>
    (sel as (s: { addToast: () => void }) => unknown)({ addToast: mockAddToast }),
}));

const SETTINGS: HotelSettingsResponse = {
  hotelId: 'h-1',
  alloggiatiAutoSend: false,
  alloggiatiCredentialsConfigured: false,
  sendReservationConfirmedEmail: false,
  sendCheckoutEmail: false,
  timezone: 'Europe/Rome',
  housekeepingDayCutoffHour: 4,
};

const ALLOGGIATI_SWITCH = /alloggiati_auto_send_label/;
const RESERVATION_EMAIL_SWITCH = /email_reservation_confirmed_label/;
const CHECKOUT_EMAIL_SWITCH = /email_checkout_label/;

const renderPage = () => render(<MemoryRouter><SettingsSystem /></MemoryRouter>);

describe('SettingsSystem', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('has no back button of its own: the settings layout provides the navigation', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue(SETTINGS);
    renderPage();
    expect(screen.queryByRole('button', { name: 'back' })).not.toBeInTheDocument();
  });

  it('tells the user when the settings fail to load instead of showing dead switches', async () => {
    vi.mocked(stayService.getHotelSettings).mockRejectedValue(new Error('boom'));
    renderPage();
    expect(await screen.findByRole('alert')).toHaveTextContent('settings_system_load_failed');
    screen.getAllByRole('switch').forEach((sw) => expect(sw).toBeDisabled());
  });

  it('does not let the greeting be edited while the settings are not loaded', async () => {
    vi.mocked(stayService.getHotelSettings).mockRejectedValue(new Error('boom'));
    renderPage();
    await screen.findByRole('alert');
    expect(screen.getByLabelText('email_greeting_label')).toBeDisabled();
  });

  it('labels the greeting field and keeps its description and character count', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue({ ...SETTINGS, emailGreetingText: 'Welcome' });
    renderPage();
    const field = await screen.findByLabelText('email_greeting_label');
    expect(field).toHaveValue('Welcome');
    expect(field).toHaveAccessibleDescription('email_greeting_desc');
    expect(screen.getByText('7/300')).toBeInTheDocument();
  });

  it('loads hotel settings and reflects the current alloggiatiAutoSend value', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue(SETTINGS);
    renderPage();
    await waitFor(() => expect(screen.getByRole('switch', { name: ALLOGGIATI_SWITCH }))
      .toHaveAttribute('aria-checked', 'false'));
  });

  it('disables all toggles while settings are still loading', () => {
    vi.mocked(stayService.getHotelSettings).mockReturnValue(new Promise(() => {}));
    renderPage();
    for (const toggle of screen.getAllByRole('switch')) {
      expect(toggle).toBeDisabled();
    }
  });

  it('toggles alloggiatiAutoSend on click', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue(SETTINGS);
    vi.mocked(stayService.updateHotelSettings).mockResolvedValue({ ...SETTINGS, alloggiatiAutoSend: true });
    renderPage();
    const toggle = screen.getByRole('switch', { name: ALLOGGIATI_SWITCH });
    await waitFor(() => expect(toggle).not.toBeDisabled());

    fireEvent.click(toggle);

    await waitFor(() => expect(stayService.updateHotelSettings).toHaveBeenCalledWith({ alloggiatiAutoSend: true }));
    await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'true'));
  });

  it('toggles the reservation-confirmed email switch on click', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue(SETTINGS);
    vi.mocked(stayService.updateHotelSettings).mockResolvedValue(
      { ...SETTINGS, sendReservationConfirmedEmail: true });
    renderPage();
    const toggle = screen.getByRole('switch', { name: RESERVATION_EMAIL_SWITCH });
    await waitFor(() => expect(toggle).not.toBeDisabled());

    fireEvent.click(toggle);

    await waitFor(() => expect(stayService.updateHotelSettings)
      .toHaveBeenCalledWith({ sendReservationConfirmedEmail: true }));
    await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'true'));
  });

  it('toggles the checkout email switch on click', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue(SETTINGS);
    vi.mocked(stayService.updateHotelSettings).mockResolvedValue({ ...SETTINGS, sendCheckoutEmail: true });
    renderPage();
    const toggle = screen.getByRole('switch', { name: CHECKOUT_EMAIL_SWITCH });
    await waitFor(() => expect(toggle).not.toBeDisabled());

    fireEvent.click(toggle);

    await waitFor(() => expect(stayService.updateHotelSettings).toHaveBeenCalledWith({ sendCheckoutEmail: true }));
  });

  it('hides the custom subject field when the reservation email toggle is off', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue(SETTINGS);
    renderPage();
    await waitFor(() => expect(screen.getByRole('switch', { name: RESERVATION_EMAIL_SWITCH }))
      .not.toBeDisabled());
    expect(screen.queryByLabelText('email_subject_label')).not.toBeInTheDocument();
  });

  it('shows and saves the custom subject field when the reservation email toggle is on', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue(
      { ...SETTINGS, sendReservationConfirmedEmail: true });
    vi.mocked(stayService.updateHotelSettings).mockResolvedValue(
      { ...SETTINGS, sendReservationConfirmedEmail: true, emailSubjectReservationConfirmed: 'Custom subject' });
    renderPage();

    const inputs = await screen.findAllByLabelText('email_subject_label');
    expect(inputs).toHaveLength(1);
    fireEvent.change(inputs[0], { target: { value: 'Custom subject' } });
    fireEvent.blur(inputs[0]);

    await waitFor(() => expect(stayService.updateHotelSettings)
      .toHaveBeenCalledWith({ emailSubjectReservationConfirmed: 'Custom subject' }));
  });

  it('saves the greeting text on blur-sm only when it changed', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue(
      { ...SETTINGS, emailGreetingText: 'See you soon' });
    vi.mocked(stayService.updateHotelSettings).mockResolvedValue(
      { ...SETTINGS, emailGreetingText: 'New greeting' });
    renderPage();

    const textarea = await screen.findByLabelText('email_greeting_label');
    expect(textarea).toHaveValue('See you soon');

    fireEvent.blur(textarea);
    expect(stayService.updateHotelSettings).not.toHaveBeenCalled();

    fireEvent.change(textarea, { target: { value: 'New greeting' } });
    fireEvent.blur(textarea);
    await waitFor(() => expect(stayService.updateHotelSettings)
      .toHaveBeenCalledWith({ emailGreetingText: 'New greeting' }));
  });

  describe('when saving fails', () => {
    beforeEach(() => {
      vi.mocked(stayService.getHotelSettings).mockResolvedValue(
        { ...SETTINGS, sendReservationConfirmedEmail: true });
    });

    it('shows the backend detail in an error toast and leaves the switch unchanged', async () => {
      vi.mocked(stayService.updateHotelSettings).mockRejectedValue(mockAxiosErrorWithDetail('HOTEL_SETTINGS_INVALID'));
      renderPage();
      const toggle = screen.getByRole('switch', { name: ALLOGGIATI_SWITCH });
      await waitFor(() => expect(toggle).not.toBeDisabled());

      fireEvent.click(toggle);

      await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('HOTEL_SETTINGS_INVALID', 'error'));
      expect(toggle).toHaveAttribute('aria-checked', 'false');
      await waitFor(() => expect(toggle).not.toBeDisabled());
    });

    it('falls back to the generic message when the error has no detail', async () => {
      vi.mocked(stayService.updateHotelSettings).mockRejectedValue(new Error('network'));
      renderPage();
      const toggle = screen.getByRole('switch', { name: CHECKOUT_EMAIL_SWITCH });
      await waitFor(() => expect(toggle).not.toBeDisabled());

      fireEvent.click(toggle);

      await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('settings_system_save_failed', 'error'));
    });

    it('reports a failed subject save', async () => {
      vi.mocked(stayService.updateHotelSettings).mockRejectedValue(new Error('network'));
      renderPage();
      const input = await screen.findByLabelText('email_subject_label');

      fireEvent.change(input, { target: { value: 'New subject' } });
      fireEvent.blur(input);

      await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('settings_system_save_failed', 'error'));
      expect(input).toHaveValue('New subject');
    });

    it('reports a failed greeting save and keeps what was typed', async () => {
      vi.mocked(stayService.updateHotelSettings).mockRejectedValue(new Error('network'));
      renderPage();
      const textarea = await screen.findByLabelText('email_greeting_label');

      fireEvent.change(textarea, { target: { value: 'Draft' } });
      fireEvent.blur(textarea);

      await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('settings_system_save_failed', 'error'));
      expect(textarea).toHaveValue('Draft');
    });
  });

  it('should have no accessibility violations', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue(
      { ...SETTINGS, sendReservationConfirmedEmail: true, sendCheckoutEmail: true });
    const { container } = renderPage();
    await waitFor(() => expect(screen.getByRole('switch', { name: ALLOGGIATI_SWITCH })).not.toBeDisabled());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
