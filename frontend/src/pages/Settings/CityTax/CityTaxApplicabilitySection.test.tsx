import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { CityTaxApplicabilitySection } from './CityTaxApplicabilitySection';
import { stayService } from '../../../services';
import { mockAxiosErrorWithDetail } from '../../../test-utils';

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

const SELECT_LABEL = 'city_tax_applicability_label';

describe('CityTaxApplicabilitySection', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(stayService.getCityTaxApplicability).mockResolvedValue({ applicability: 'UNKNOWN' });
  });

  it('loads and shows the current applicability', async () => {
    vi.mocked(stayService.getCityTaxApplicability).mockResolvedValue({ applicability: 'NOT_APPLICABLE' });
    render(<CityTaxApplicabilitySection />);

    await waitFor(() => expect(screen.getByLabelText(SELECT_LABEL)).toHaveValue('NOT_APPLICABLE'));
  });

  it('saves a new applicability on change', async () => {
    vi.mocked(stayService.updateCityTaxApplicability).mockResolvedValue({ applicability: 'APPLICABLE' });
    render(<CityTaxApplicabilitySection />);
    await waitFor(() => expect(screen.getByLabelText(SELECT_LABEL)).toHaveValue('UNKNOWN'));

    fireEvent.change(screen.getByLabelText(SELECT_LABEL), { target: { value: 'APPLICABLE' } });

    await waitFor(() => expect(stayService.updateCityTaxApplicability)
      .toHaveBeenCalledWith({ applicability: 'APPLICABLE' }));
    expect(mockAddToast).toHaveBeenCalledWith('save', 'success');
  });

  it('reverts the selection and shows an error toast when saving fails', async () => {
    vi.mocked(stayService.updateCityTaxApplicability)
      .mockRejectedValue(mockAxiosErrorWithDetail('INTERNAL_SERVER_ERROR', 500));
    render(<CityTaxApplicabilitySection />);
    await waitFor(() => expect(screen.getByLabelText(SELECT_LABEL)).toHaveValue('UNKNOWN'));

    fireEvent.change(screen.getByLabelText(SELECT_LABEL), { target: { value: 'APPLICABLE' } });

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('INTERNAL_SERVER_ERROR', 'error'));
    expect(screen.getByLabelText(SELECT_LABEL)).toHaveValue('UNKNOWN');
  });

  it('passes axe accessibility check', async () => {
    const { container } = render(<CityTaxApplicabilitySection />);
    await waitFor(() => expect(screen.getByLabelText(SELECT_LABEL)).toHaveValue('UNKNOWN'));
    expect(await axe(container)).toHaveNoViolations();
  });
});
