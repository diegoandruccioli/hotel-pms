import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { SettingsCityTax } from './SettingsCityTax';
import { stayService } from '../../services/stayService';
import { mockAxiosErrorWithDetail } from '../../test-utils/mockAxiosError';
import type { CityTaxRateResponse, HotelCategoryHistoryResponse } from '../../types/stay.types';

const stableT = (key: string) => key;
const stableI18n = { language: 'en' };
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: stableT, i18n: stableI18n }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/stayService');
const mockAddToast = vi.fn();
vi.mock('../../store/toastStore', () => ({
  useToastStore: (sel: unknown) =>
    (sel as (s: { addToast: () => void }) => unknown)({ addToast: mockAddToast }),
}));

const CATEGORY_ENTRY: HotelCategoryHistoryResponse = {
  id: 'ch1', category: '4_STAR', validFrom: '2026-01-01', validTo: null,
};

const RATE: CityTaxRateResponse = {
  id: 'r1', comuneCodice: '099014000', category: '4_STAR',
  amountPerNight: 2.5, maxTaxableNights: 7, exemptUnderAge: 14,
  validFrom: '2026-01-01', validTo: null, note: null,
};

const renderPage = () =>
  render(
    <MemoryRouter>
      <SettingsCityTax />
    </MemoryRouter>,
  );

describe('SettingsCityTax', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(stayService.getHotelCategoryHistory).mockResolvedValue([]);
    vi.mocked(stayService.getCityTaxRates).mockResolvedValue([]);
  });

  it('renders the page title', async () => {
    renderPage();
    expect(screen.getByText('settings_section_city_tax')).toBeInTheDocument();
  });

  it('shows empty states when no category history and no rates exist', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('city_tax_no_category_history')).toBeInTheDocument());
    expect(screen.getByText('city_tax_no_rates')).toBeInTheDocument();
  });

  it('shows current category and history row after data loads', async () => {
    vi.mocked(stayService.getHotelCategoryHistory).mockResolvedValue([CATEGORY_ENTRY]);
    renderPage();
    await waitFor(() => expect(screen.getByText('city_tax_current_category')).toBeInTheDocument());
    expect(screen.getByText('4_STAR')).toBeInTheDocument();
  });

  it('shows a rate row after data loads', async () => {
    vi.mocked(stayService.getCityTaxRates).mockResolvedValue([RATE]);
    renderPage();
    await waitFor(() => expect(screen.getByText('€ 2.50')).toBeInTheDocument());
  });

  it('submits a new category entry and reloads the history', async () => {
    vi.mocked(stayService.recordHotelCategory).mockResolvedValue(CATEGORY_ENTRY);
    renderPage();
    await waitFor(() => expect(screen.getByText('city_tax_no_category_history')).toBeInTheDocument());

    fireEvent.change(screen.getAllByLabelText(/city_tax_category \*/i)[0], { target: { value: '4_STAR' } });
    fireEvent.change(screen.getAllByLabelText(/city_tax_valid_from \*/i)[0], { target: { value: '2026-06-01' } });
    fireEvent.click(screen.getByText('city_tax_add_category'));

    await waitFor(() => expect(stayService.recordHotelCategory).toHaveBeenCalledWith({
      category: '4_STAR', validFrom: '2026-06-01',
    }));
    expect(stayService.getHotelCategoryHistory).toHaveBeenCalledTimes(2);
  });

  it('blocks category submission when the category field is blank', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByText('city_tax_no_category_history')).toBeInTheDocument());

    fireEvent.change(screen.getAllByLabelText(/city_tax_valid_from \*/i)[0], { target: { value: '2026-06-01' } });
    fireEvent.click(screen.getByText('city_tax_add_category'));

    expect(await screen.findByText('common:err_required')).toBeInTheDocument();
    expect(stayService.recordHotelCategory).not.toHaveBeenCalled();
  });

  it('submits a new rate with optional fields converted to numbers', async () => {
    vi.mocked(stayService.createCityTaxRate).mockResolvedValue(RATE);
    renderPage();
    await waitFor(() => expect(screen.getByText('city_tax_no_rates')).toBeInTheDocument());

    fireEvent.change(screen.getAllByLabelText(/city_tax_category \*/i)[1], { target: { value: '4_STAR' } });
    fireEvent.change(screen.getByLabelText(/city_tax_amount_per_night/i), { target: { value: '2.50' } });
    fireEvent.change(screen.getByLabelText(/city_tax_max_taxable_nights/i), { target: { value: '7' } });
    fireEvent.change(screen.getByLabelText(/city_tax_exempt_under_age/i), { target: { value: '14' } });
    fireEvent.change(screen.getAllByLabelText(/city_tax_valid_from \*/i)[1], { target: { value: '2026-06-01' } });
    fireEvent.click(screen.getByText('city_tax_add_rate'));

    await waitFor(() => expect(stayService.createCityTaxRate).toHaveBeenCalledWith({
      category: '4_STAR', amountPerNight: 2.5, maxTaxableNights: 7, exemptUnderAge: 14,
      validFrom: '2026-06-01', note: undefined,
    }));
  });

  it('shows the backend detail message on a 409 rate overlap', async () => {
    // Two distinct 400s exist server-side (CITY_TAX_COMUNE_NOT_CONFIGURED vs.
    // CITY_TAX_RATE_VALID_FROM_NOT_AFTER_CURRENT), so the component no longer
    // branches on HTTP status — it surfaces the backend's `detail` code, which
    // the real Axios interceptor translates via locales/*/errors.json.
    vi.mocked(stayService.createCityTaxRate)
      .mockRejectedValue(mockAxiosErrorWithDetail('CITY_TAX_RATE_OVERLAP', 409));
    renderPage();
    await waitFor(() => expect(screen.getByText('city_tax_no_rates')).toBeInTheDocument());

    fireEvent.change(screen.getAllByLabelText(/city_tax_category \*/i)[1], { target: { value: '4_STAR' } });
    fireEvent.change(screen.getByLabelText(/city_tax_amount_per_night/i), { target: { value: '2.50' } });
    fireEvent.change(screen.getAllByLabelText(/city_tax_valid_from \*/i)[1], { target: { value: '2026-06-01' } });
    fireEvent.click(screen.getByText('city_tax_add_rate'));

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('CITY_TAX_RATE_OVERLAP', 'error'));
  });

  it('shows the backend detail message when the comune is not configured (400)', async () => {
    vi.mocked(stayService.createCityTaxRate)
      .mockRejectedValue(mockAxiosErrorWithDetail('CITY_TAX_COMUNE_NOT_CONFIGURED', 400));
    renderPage();
    await waitFor(() => expect(screen.getByText('city_tax_no_rates')).toBeInTheDocument());

    fireEvent.change(screen.getAllByLabelText(/city_tax_category \*/i)[1], { target: { value: '4_STAR' } });
    fireEvent.change(screen.getByLabelText(/city_tax_amount_per_night/i), { target: { value: '2.50' } });
    fireEvent.change(screen.getAllByLabelText(/city_tax_valid_from \*/i)[1], { target: { value: '2026-06-01' } });
    fireEvent.click(screen.getByText('city_tax_add_rate'));

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('CITY_TAX_COMUNE_NOT_CONFIGURED', 'error'));
  });

  it('shows the backend detail message when the new rate does not start after the current one (400)', async () => {
    vi.mocked(stayService.createCityTaxRate)
      .mockRejectedValue(mockAxiosErrorWithDetail('CITY_TAX_RATE_VALID_FROM_NOT_AFTER_CURRENT', 400));
    renderPage();
    await waitFor(() => expect(screen.getByText('city_tax_no_rates')).toBeInTheDocument());

    fireEvent.change(screen.getAllByLabelText(/city_tax_category \*/i)[1], { target: { value: '4_STAR' } });
    fireEvent.change(screen.getByLabelText(/city_tax_amount_per_night/i), { target: { value: '2.50' } });
    fireEvent.change(screen.getAllByLabelText(/city_tax_valid_from \*/i)[1], { target: { value: '2025-01-01' } });
    fireEvent.click(screen.getByText('city_tax_add_rate'));

    await waitFor(() => expect(mockAddToast)
      .toHaveBeenCalledWith('CITY_TAX_RATE_VALID_FROM_NOT_AFTER_CURRENT', 'error'));
  });

  it('shows the backend detail message on a generic failure', async () => {
    vi.mocked(stayService.createCityTaxRate).mockRejectedValue(mockAxiosErrorWithDetail('CITY_TAX_RATE_INVALID', 422));
    renderPage();
    await waitFor(() => expect(screen.getByText('city_tax_no_rates')).toBeInTheDocument());

    fireEvent.change(screen.getAllByLabelText(/city_tax_category \*/i)[1], { target: { value: '4_STAR' } });
    fireEvent.change(screen.getByLabelText(/city_tax_amount_per_night/i), { target: { value: '2.50' } });
    fireEvent.change(screen.getAllByLabelText(/city_tax_valid_from \*/i)[1], { target: { value: '2026-06-01' } });
    fireEvent.click(screen.getByText('city_tax_add_rate'));

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('CITY_TAX_RATE_INVALID', 'error'));
  });

  it('passes axe accessibility check', async () => {
    vi.mocked(stayService.getHotelCategoryHistory).mockResolvedValue([CATEGORY_ENTRY]);
    vi.mocked(stayService.getCityTaxRates).mockResolvedValue([RATE]);
    const { container } = renderPage();
    await waitFor(() => expect(screen.getAllByText('4_STAR').length).toBeGreaterThan(0));
    expect(await axe(container)).toHaveNoViolations();
  });
});
