import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { SettingsCityTax } from './SettingsCityTax';
import { stayService } from '../../services';
import type { CityTaxRateResponse, HotelCategoryHistoryResponse } from '../../types';

// The page only composes the four sections; each one is tested next to its component
// in ./CityTax. Here: the page mounts all of them, and the composed page is accessible.
const stableT = (key: string) => key;
const stableI18n = { language: 'en' };
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: stableT, i18n: stableI18n }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/stayService');

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
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
    vi.mocked(stayService.getCityTaxApplicability).mockResolvedValue({ applicability: 'UNKNOWN' });
  });

  it('renders the page title', () => {
    renderPage();
    expect(screen.getByText('settings_section_city_tax')).toBeInTheDocument();
  });

  it('mounts the applicability, category, rates and backfill sections', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByLabelText('city_tax_applicability_label')).toBeInTheDocument());
    expect(await screen.findByText('city_tax_no_category_history')).toBeInTheDocument();
    expect(await screen.findByText('city_tax_no_rates')).toBeInTheDocument();
    expect(screen.getByText('city_tax_backfill_action_preview')).toBeInTheDocument();
  });

  it('passes axe accessibility check', async () => {
    vi.mocked(stayService.getHotelCategoryHistory).mockResolvedValue([CATEGORY_ENTRY]);
    vi.mocked(stayService.getCityTaxRates).mockResolvedValue([RATE]);
    const { container } = renderPage();
    await waitFor(() => expect(screen.getAllByText('4_STAR').length).toBeGreaterThan(0));
    expect(await axe(container)).toHaveNoViolations();
  });
});
