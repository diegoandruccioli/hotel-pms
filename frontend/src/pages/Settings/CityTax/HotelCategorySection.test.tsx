import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { HotelCategorySection } from './HotelCategorySection';
import { stayService } from '../../../services';
import type { HotelCategoryHistoryResponse } from '../../../types';

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

const CATEGORY_ENTRY: HotelCategoryHistoryResponse = {
  id: 'ch1', category: '4_STAR', validFrom: '2026-01-01', validTo: null,
};

describe('HotelCategorySection', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(stayService.getHotelCategoryHistory).mockResolvedValue([]);
  });

  it('shows the empty state when no category history exists', async () => {
    render(<HotelCategorySection />);
    await waitFor(() => expect(screen.getByText('city_tax_no_category_history')).toBeInTheDocument());
  });

  it('shows the current category and the history row after data loads', async () => {
    vi.mocked(stayService.getHotelCategoryHistory).mockResolvedValue([CATEGORY_ENTRY]);
    render(<HotelCategorySection />);
    await waitFor(() => expect(screen.getByText('city_tax_current_category')).toBeInTheDocument());
    expect(screen.getByText('4_STAR')).toBeInTheDocument();
  });

  it('submits a new category entry and reloads the history', async () => {
    vi.mocked(stayService.recordHotelCategory).mockResolvedValue(CATEGORY_ENTRY);
    render(<HotelCategorySection />);
    await waitFor(() => expect(screen.getByText('city_tax_no_category_history')).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/city_tax_category \*/i), { target: { value: '4_STAR' } });
    fireEvent.change(screen.getByLabelText(/city_tax_valid_from \*/i), { target: { value: '2026-06-01' } });
    fireEvent.click(screen.getByText('city_tax_add_category'));

    await waitFor(() => expect(stayService.recordHotelCategory).toHaveBeenCalledWith({
      category: '4_STAR', validFrom: '2026-06-01',
    }));
    expect(stayService.getHotelCategoryHistory).toHaveBeenCalledTimes(2);
  });

  it('blocks submission when the category field is blank', async () => {
    render(<HotelCategorySection />);
    await waitFor(() => expect(screen.getByText('city_tax_no_category_history')).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/city_tax_valid_from \*/i), { target: { value: '2026-06-01' } });
    fireEvent.click(screen.getByText('city_tax_add_category'));

    expect(await screen.findByText('common:err_required')).toBeInTheDocument();
    expect(stayService.recordHotelCategory).not.toHaveBeenCalled();
  });

  it('passes axe accessibility check', async () => {
    vi.mocked(stayService.getHotelCategoryHistory).mockResolvedValue([CATEGORY_ENTRY]);
    const { container } = render(<HotelCategorySection />);
    await waitFor(() => expect(screen.getAllByText('4_STAR').length).toBeGreaterThan(0));
    expect(await axe(container)).toHaveNoViolations();
  });
});
