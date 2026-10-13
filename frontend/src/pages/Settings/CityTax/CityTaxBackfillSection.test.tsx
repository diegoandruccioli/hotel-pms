import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { CityTaxBackfillSection } from './CityTaxBackfillSection';
import { stayService } from '../../../services';
import { mockAxiosErrorWithDetail } from '../../../test-utils';
import type { CityTaxBackfillResponse } from '../../../types';

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

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const PREVIEW_ACTION = 'city_tax_backfill_action_preview';
const CONFIRM_ACTION = 'city_tax_backfill_action_confirm';

const chargeablePreview = (): CityTaxBackfillResponse => ({
  lines: [{ stayId: 's1', checkInDate: '2026-05-01', amount: 2.5, charged: false, skipReason: null }],
  totalAmount: 2.5,
  chargedCount: 0,
  skippedCount: 0,
});

const previewAndWaitForConfirm = async () => {
  render(<CityTaxBackfillSection />);
  fireEvent.click(screen.getByText(PREVIEW_ACTION));
  await waitFor(() => expect(screen.getByText(CONFIRM_ACTION)).toBeInTheDocument());
};

describe('CityTaxBackfillSection', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('previews without charging or writing anything', async () => {
    vi.mocked(stayService.previewCityTaxBackfill).mockResolvedValue(chargeablePreview());
    render(<CityTaxBackfillSection />);

    fireEvent.click(screen.getByText(PREVIEW_ACTION));

    await waitFor(() => expect(stayService.previewCityTaxBackfill).toHaveBeenCalled());
    expect(screen.getByText('2026-05-01')).toBeInTheDocument();
    expect(stayService.confirmCityTaxBackfill).not.toHaveBeenCalled();
    expect(screen.getByText(CONFIRM_ACTION)).toBeInTheDocument();
  });

  it('shows no confirm action when the preview has nothing chargeable', async () => {
    vi.mocked(stayService.previewCityTaxBackfill).mockResolvedValue({
      lines: [{ stayId: 's1', checkInDate: '2026-05-01', amount: 2.5, charged: false, skipReason: 'INVOICE_NOT_OPEN' }],
      totalAmount: 2.5,
      chargedCount: 0,
      skippedCount: 1,
    });
    render(<CityTaxBackfillSection />);

    fireEvent.click(screen.getByText(PREVIEW_ACTION));

    await waitFor(() => expect(screen.getByText('2026-05-01')).toBeInTheDocument());
    expect(screen.queryByText(CONFIRM_ACTION)).not.toBeInTheDocument();
  });

  it('confirms and charges after a preview', async () => {
    vi.mocked(stayService.previewCityTaxBackfill).mockResolvedValue(chargeablePreview());
    vi.mocked(stayService.confirmCityTaxBackfill).mockResolvedValue({
      lines: [{ stayId: 's1', checkInDate: '2026-05-01', amount: 2.5, charged: true, skipReason: null }],
      totalAmount: 2.5,
      chargedCount: 1,
      skippedCount: 0,
    });
    await previewAndWaitForConfirm();

    fireEvent.click(screen.getByText(CONFIRM_ACTION));
    // Charging guests is not undone by a second click: it asks first.
    expect(stayService.confirmCityTaxBackfill).not.toHaveBeenCalled();
    expect(screen.getByText('city_tax_backfill_confirm_title')).toBeInTheDocument();
    fireEvent.click(screen.getByText('city_tax_backfill_confirm_yes'));

    await waitFor(() => expect(stayService.confirmCityTaxBackfill).toHaveBeenCalled());
    expect(mockAddToast).toHaveBeenCalledWith('city_tax_backfill_success', 'success');
    // Confirmed — the action disappears rather than allowing a duplicate charge.
    await waitFor(() => expect(screen.queryByText(CONFIRM_ACTION)).not.toBeInTheDocument());
  });

  it('charges nothing when the confirmation is cancelled', async () => {
    vi.mocked(stayService.previewCityTaxBackfill).mockResolvedValue(chargeablePreview());
    await previewAndWaitForConfirm();

    fireEvent.click(screen.getByText(CONFIRM_ACTION));
    fireEvent.click(screen.getByText('cancel'));

    expect(stayService.confirmCityTaxBackfill).not.toHaveBeenCalled();
    expect(screen.queryByText('city_tax_backfill_confirm_title')).not.toBeInTheDocument();
    expect(screen.getByText(CONFIRM_ACTION)).toBeInTheDocument();
  });

  it('shows an empty state when no unassessed stays are found', async () => {
    vi.mocked(stayService.previewCityTaxBackfill).mockResolvedValue({
      lines: [], totalAmount: 0, chargedCount: 0, skippedCount: 0,
    });
    render(<CityTaxBackfillSection />);

    fireEvent.click(screen.getByText(PREVIEW_ACTION));

    await waitFor(() => expect(screen.getByText('city_tax_backfill_none_found')).toBeInTheDocument());
  });

  it('shows an error toast when the preview fails', async () => {
    vi.mocked(stayService.previewCityTaxBackfill)
      .mockRejectedValue(mockAxiosErrorWithDetail('INTERNAL_SERVER_ERROR', 500));
    render(<CityTaxBackfillSection />);

    fireEvent.click(screen.getByText(PREVIEW_ACTION));

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('INTERNAL_SERVER_ERROR', 'error'));
  });

  it('passes axe accessibility check after a preview', async () => {
    vi.mocked(stayService.previewCityTaxBackfill).mockResolvedValue(chargeablePreview());
    const { container } = render(<CityTaxBackfillSection />);
    fireEvent.click(screen.getByText(PREVIEW_ACTION));
    await waitFor(() => expect(screen.getByText('2026-05-01')).toBeInTheDocument());
    expect(await axe(container)).toHaveNoViolations();
  });
});
