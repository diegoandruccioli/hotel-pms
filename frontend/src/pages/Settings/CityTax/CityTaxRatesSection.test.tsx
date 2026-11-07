import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { CityTaxRatesSection } from './CityTaxRatesSection';
import { stayService } from '../../../services';
import { mockAxiosErrorWithDetail } from '../../../test-utils';
import type { CityTaxRateResponse } from '../../../types';

const stableT = (key: string) => key;
const stableI18n = { language: 'en' };
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: stableT, i18n: stableI18n }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../../services/stayService');
const mockAddToast = vi.fn();
vi.mock('../../../store/toastStore', () => ({
  useToastStore: (sel: unknown) =>
    (sel as (s: { addToast: () => void }) => unknown)({ addToast: mockAddToast }),
}));

const RATE: CityTaxRateResponse = {
  id: 'r1', comuneCodice: '099014000', category: '4_STAR',
  amountPerNight: 2.5, maxTaxableNights: 7, exemptUnderAge: 14,
  validFrom: '2026-01-01', validTo: null, note: null,
};

const fillRequiredFields = (validFrom: string) => {
  fireEvent.change(screen.getByLabelText(/city_tax_category \*/i), { target: { value: '4_STAR' } });
  fireEvent.change(screen.getByLabelText(/city_tax_amount_per_night/i), { target: { value: '2.50' } });
  fireEvent.change(screen.getByLabelText(/city_tax_valid_from \*/i), { target: { value: validFrom } });
};

const submitFailingRate = async (detail: string, status: number, validFrom: string) => {
  vi.mocked(stayService.createCityTaxRate).mockRejectedValue(mockAxiosErrorWithDetail(detail, status));
  render(<CityTaxRatesSection />);
  await waitFor(() => expect(screen.getByText('city_tax_no_rates')).toBeInTheDocument());

  fillRequiredFields(validFrom);
  fireEvent.click(screen.getByText('city_tax_add_rate'));

  await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith(detail, 'error'));
};

describe('CityTaxRatesSection', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(stayService.getCityTaxRates).mockResolvedValue([]);
  });

  it('shows the empty state when no rates exist', async () => {
    render(<CityTaxRatesSection />);
    await waitFor(() => expect(screen.getByText('city_tax_no_rates')).toBeInTheDocument());
  });

  it('shows a rate row after data loads', async () => {
    vi.mocked(stayService.getCityTaxRates).mockResolvedValue([RATE]);
    render(<CityTaxRatesSection />);
    await waitFor(() => expect(screen.getByText('€2.50')).toBeInTheDocument());
  });

  it('submits a new rate with optional fields converted to numbers', async () => {
    vi.mocked(stayService.createCityTaxRate).mockResolvedValue(RATE);
    render(<CityTaxRatesSection />);
    await waitFor(() => expect(screen.getByText('city_tax_no_rates')).toBeInTheDocument());

    fillRequiredFields('2026-06-01');
    fireEvent.change(screen.getByLabelText(/city_tax_max_taxable_nights/i), { target: { value: '7' } });
    fireEvent.change(screen.getByLabelText(/city_tax_exempt_under_age/i), { target: { value: '14' } });
    fireEvent.click(screen.getByText('city_tax_add_rate'));

    await waitFor(() => expect(stayService.createCityTaxRate).toHaveBeenCalledWith({
      category: '4_STAR', amountPerNight: 2.5, maxTaxableNights: 7, exemptUnderAge: 14,
      validFrom: '2026-06-01', note: undefined,
    }));
  });

  // Two distinct 400s exist server-side (CITY_TAX_COMUNE_NOT_CONFIGURED vs.
  // CITY_TAX_RATE_VALID_FROM_NOT_AFTER_CURRENT), so the component no longer
  // branches on HTTP status — it surfaces the backend's `detail` code, which
  // the real Axios interceptor translates via locales/*/errors.json.
  it('shows the backend detail message on a 409 rate overlap', async () => {
    await submitFailingRate('CITY_TAX_RATE_OVERLAP', 409, '2026-06-01');
  });

  it('shows the backend detail message when the comune is not configured (400)', async () => {
    await submitFailingRate('CITY_TAX_COMUNE_NOT_CONFIGURED', 400, '2026-06-01');
  });

  it('shows the backend detail message when the new rate does not start after the current one (400)', async () => {
    await submitFailingRate('CITY_TAX_RATE_VALID_FROM_NOT_AFTER_CURRENT', 400, '2025-01-01');
  });

  it('shows the backend detail message on a generic failure', async () => {
    await submitFailingRate('CITY_TAX_RATE_INVALID', 422, '2026-06-01');
  });

  it('passes axe accessibility check', async () => {
    vi.mocked(stayService.getCityTaxRates).mockResolvedValue([RATE]);
    const { container } = render(<CityTaxRatesSection />);
    await waitFor(() => expect(screen.getByText('€2.50')).toBeInTheDocument());
    expect(await axe(container)).toHaveNoViolations();
  });
});
