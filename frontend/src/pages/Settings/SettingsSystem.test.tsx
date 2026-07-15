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
  hotelId: 'h-1',
  alloggiatiAutoSend: false,
  alloggiatiCredentialsConfigured: false,
  sendReservationConfirmedEmail: false,
  sendCheckoutEmail: false,
};

const ALLOGGIATI_SWITCH = /alloggiati_auto_send_label/;
const RESERVATION_EMAIL_SWITCH = /email_reservation_confirmed_label/;
const CHECKOUT_EMAIL_SWITCH = /email_checkout_label/;

const renderPage = () => render(<MemoryRouter><SettingsSystem /></MemoryRouter>);

describe('SettingsSystem', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('navigates back in history when the back button is clicked', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue(SETTINGS);
    renderPage();
    fireEvent.click(screen.getByRole('button', { name: 'back' }));
    expect(mockNavigate).toHaveBeenCalledWith(-1);
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

  it('saves the greeting text on blur only when it changed', async () => {
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

  it('should have no accessibility violations', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue(
      { ...SETTINGS, sendReservationConfirmedEmail: true, sendCheckoutEmail: true });
    const { container } = renderPage();
    await waitFor(() => expect(screen.getByRole('switch', { name: ALLOGGIATI_SWITCH })).not.toBeDisabled());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
