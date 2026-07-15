import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import userEvent from '@testing-library/user-event';
import { HotelProfile } from './HotelProfile';
import { stayService } from '../services/stayService';
import type { HotelSettingsResponse } from '../types/stay.types';

// `t`/`i18n` must be module-level stable references: HotelProfile's settings-load
// useEffect depends on `t`, so an inline arrow recreated on every useTranslation()
// call would give `t` a new identity every render, silently re-firing the fetch
// (and resetting form state) after every interaction in this file.
const stableT = (key: string) => key;
const stableI18n = { language: 'en' };
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: stableT,
    i18n: stableI18n,
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/stayService');
const mockAddToast = vi.fn();
vi.mock('../store/toastStore', () => ({
  useToastStore: () => ({ addToast: mockAddToast }),
}));

const baseSettings: HotelSettingsResponse = {
  hotelId: 'h1',
  alloggiatiAutoSend: false,
  hotelName: 'Hotel Test',
  address: 'Via Roma 1',
  vatNumber: '12345678901',
  fiscalCode: 'ABCDEF12G34H567I',
  logoUrl: '',
  alloggiatiUsername: null,
  alloggiatiCredentialsConfigured: false,
  sendReservationConfirmedEmail: true,
  sendCheckoutEmail: true,
};

const renderComponent = () =>
  render(
    <MemoryRouter>
      <HotelProfile />
    </MemoryRouter>,
  );

describe('HotelProfile', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(stayService.getHotelSettings).mockResolvedValue(baseSettings);
    vi.mocked(stayService.updateHotelSettings).mockResolvedValue(baseSettings);
  });

  it('renders the profile form with all fields including the alloggiati toggle', async () => {
    renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());

    expect(screen.getByLabelText(/label_hotel_name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/label_hotel_address/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/label_vat_number/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/label_fiscal_code/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/label_logo_url/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/label_alloggiati_auto_send/i)).toBeInTheDocument();
  });

  it('loads alloggiatiAutoSend=false and renders checkbox unchecked', async () => {
    renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());

    const toggle = screen.getByRole('checkbox', { name: /label_alloggiati_auto_send/i });
    expect(toggle).not.toBeChecked();
  });

  it('loads alloggiatiAutoSend=true and renders checkbox checked', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue({
      ...baseSettings,
      alloggiatiAutoSend: true,
    });
    renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());

    const toggle = screen.getByRole('checkbox', { name: /label_alloggiati_auto_send/i });
    expect(toggle).toBeChecked();
  });

  it('shows the toggle hint text below the checkbox', async () => {
    renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());
    expect(screen.getByText('hint_alloggiati_auto_send')).toBeInTheDocument();
  });

  it('when backend returns alloggiatiAutoSend=true, save preserves the value', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue({
      ...baseSettings,
      alloggiatiAutoSend: true,
    });
    renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /btn_save_profile/i }));
    await waitFor(() => {
      expect(stayService.updateHotelSettings).toHaveBeenCalledWith(
        expect.objectContaining({ alloggiatiAutoSend: true }),
      );
    });
  });

  it('saving with toggle unchecked sends alloggiatiAutoSend: false', async () => {
    renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /btn_save_profile/i }));

    await waitFor(() => {
      expect(stayService.updateHotelSettings).toHaveBeenCalledWith(
        expect.objectContaining({ alloggiatiAutoSend: false }),
      );
    });
  });

  it('toggles alloggiatiAutoSend via the checkbox', async () => {
    renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());

    const toggle = screen.getByRole('checkbox', { name: /label_alloggiati_auto_send/i });
    expect(toggle).not.toBeChecked();
    fireEvent.click(toggle);
    expect(toggle).toBeChecked();
  });

  it('hides the logo preview image on load error', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue({ ...baseSettings, logoUrl: 'https://example.com/logo.png' });
    renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());

    const img = screen.getByAltText('hotel logo preview') as HTMLImageElement;
    fireEvent.error(img);
    expect(img.style.display).toBe('none');
  });

  it('shows an error toast when loading settings fails', async () => {
    vi.mocked(stayService.getHotelSettings).mockRejectedValue(new Error('boom'));
    renderComponent();
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('err_profile_save', 'error'));
  });

  it('shows an error toast when saving settings fails', async () => {
    vi.mocked(stayService.updateHotelSettings).mockRejectedValueOnce(new Error('boom'));
    renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: /btn_save_profile/i }));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('err_profile_save', 'error'));
  });

  it('renders Alloggiati credential fields, all blank, when none are configured', async () => {
    renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());

    expect(screen.getByLabelText(/label_alloggiati_username/i)).toHaveValue('');
    expect(screen.getByLabelText(/label_alloggiati_password/i)).toHaveValue('');
    expect(screen.getByLabelText(/label_alloggiati_ws_key/i)).toHaveValue('');
    expect(screen.getByText('status_alloggiati_credentials_not_configured')).toBeInTheDocument();
  });

  it('pre-fills the username but never the password/WsKey when credentials are configured', async () => {
    vi.mocked(stayService.getHotelSettings).mockResolvedValue({
      ...baseSettings,
      alloggiatiUsername: 'hotelUser',
      alloggiatiCredentialsConfigured: true,
    });
    renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());

    expect(screen.getByLabelText(/label_alloggiati_username/i)).toHaveValue('hotelUser');
    expect(screen.getByLabelText(/label_alloggiati_password/i)).toHaveValue('');
    expect(screen.getByLabelText(/label_alloggiati_ws_key/i)).toHaveValue('');
    expect(screen.getByText('status_alloggiati_credentials_configured')).toBeInTheDocument();
  });

  it('uses password-type inputs for the secret fields', async () => {
    renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());

    expect(screen.getByLabelText(/label_alloggiati_password/i)).toHaveAttribute('type', 'password');
    expect(screen.getByLabelText(/label_alloggiati_ws_key/i)).toHaveAttribute('type', 'password');
  });

  it('toggles visibility independently for the password and WS key fields', async () => {
    renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());

    const passwordInput = screen.getByLabelText(/label_alloggiati_password/i);
    const wsKeyInput = screen.getByLabelText(/label_alloggiati_ws_key/i);
    const [showPasswordToggle, showWsKeyToggle] = screen.getAllByLabelText('show_password');

    fireEvent.click(showPasswordToggle);
    expect(passwordInput).toHaveAttribute('type', 'text');
    expect(wsKeyInput).toHaveAttribute('type', 'password');

    fireEvent.click(showWsKeyToggle);
    expect(wsKeyInput).toHaveAttribute('type', 'text');
  });

  it('saves the entered username/password/WsKey and clears the secret fields afterwards', async () => {
    vi.mocked(stayService.updateHotelSettings).mockResolvedValue({
      ...baseSettings,
      alloggiatiUsername: 'newUser',
      alloggiatiCredentialsConfigured: true,
    });
    renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());

    const user = userEvent.setup();
    await user.type(screen.getByLabelText(/label_alloggiati_username/i), 'newUser');
    await user.type(screen.getByLabelText(/label_alloggiati_password/i), 'newPass');
    await user.type(screen.getByLabelText(/label_alloggiati_ws_key/i), 'newKey');
    await user.click(screen.getByRole('button', { name: /btn_save_profile/i }));

    await waitFor(() => {
      expect(stayService.updateHotelSettings).toHaveBeenCalledWith(expect.objectContaining({
        alloggiatiUsername: 'newUser',
        alloggiatiPassword: 'newPass',
        alloggiatiWsKey: 'newKey',
      }));
    });

    await waitFor(() => expect(screen.getByLabelText(/label_alloggiati_password/i)).toHaveValue(''));
    expect(screen.getByLabelText(/label_alloggiati_ws_key/i)).toHaveValue('');
    await waitFor(() => {
      expect(screen.getByText('status_alloggiati_credentials_configured')).toBeInTheDocument();
    });
  });

  it('blocks save and shows an error when VAT number is malformed', async () => {
    renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/label_vat_number/i), { target: { value: 'not-a-vat' } });
    fireEvent.click(screen.getByRole('button', { name: /btn_save_profile/i }));

    expect(await screen.findByText('common:err_invalid_vat')).toBeInTheDocument();
    expect(stayService.updateHotelSettings).not.toHaveBeenCalled();
  });

  it('should have no accessibility violations', async () => {
    const { container } = renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  }, 30000);
});
