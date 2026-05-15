import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import userEvent from '@testing-library/user-event';
import { HotelProfile } from './HotelProfile';
import { stayService } from '../services/stayService';
import type { HotelSettingsResponse } from '../types/stay.types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/stayService');
vi.mock('../store/toastStore', () => ({
  useToastStore: () => ({ addToast: vi.fn() }),
}));

const baseSettings: HotelSettingsResponse = {
  hotelId: 'h1',
  alloggiatiAutoSend: false,
  hotelName: 'Hotel Test',
  address: 'Via Roma 1',
  vatNumber: '12345678901',
  fiscalCode: 'ABCDEF12G34H567I',
  logoUrl: '',
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

  it('should have no accessibility violations', async () => {
    const { container } = renderComponent();
    await waitFor(() => expect(screen.getByText('hotel_profile_title')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  }, 30000);
});
