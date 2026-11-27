import { createElement, Fragment, type ReactNode } from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { HotelCategorySection } from './HotelCategorySection';
import { stayService } from '../../../services';
import { renderGuarded } from '../../../test-utils/renderGuarded';
import type { HotelCategoryHistoryResponse } from '../../../types';

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: ReactNode }) => createElement(Fragment, null, children),
}));

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

  it('shows a load error with a retry, not a false empty history, when loading fails', async () => {
    vi.mocked(stayService.getHotelCategoryHistory).mockReset();
    vi.mocked(stayService.getHotelCategoryHistory).mockRejectedValueOnce(new Error('boom'));
    vi.mocked(stayService.getHotelCategoryHistory).mockResolvedValueOnce([CATEGORY_ENTRY]);
    render(<HotelCategorySection />);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('city_tax_category_load_title');
    expect(screen.getByRole('heading', { level: 3, name: 'city_tax_category_load_title' })).toBeInTheDocument();
    expect(screen.queryByText('city_tax_no_category_history')).not.toBeInTheDocument();
    expect(mockAddToast).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'common:try_again' }));

    await waitFor(() => expect(screen.getByText('4_STAR')).toBeInTheDocument());
    expect(stayService.getHotelCategoryHistory).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('shows the current category and the history row after data loads', async () => {
    vi.mocked(stayService.getHotelCategoryHistory).mockResolvedValue([CATEGORY_ENTRY]);
    render(<HotelCategorySection />);
    await waitFor(() => expect(screen.getByText('city_tax_current_category')).toBeInTheDocument());
    expect(screen.getByText('4_STAR')).toBeInTheDocument();
  });

  describe('unsaved changes guard', () => {
    const renderGuardedSection = async () => {
      const view = renderGuarded(<HotelCategorySection />);
      await waitFor(() => expect(screen.getByText('city_tax_no_category_history')).toBeInTheDocument());
      return view;
    };

    it('lets the user leave while the form is empty', async () => {
      const { router } = await renderGuardedSection();
      fireEvent.click(screen.getByText('leave page'));
      expect(router.state.location.pathname).toBe('/elsewhere');
    });

    it('asks before leaving with a half-filled category, naming it', async () => {
      const { router } = await renderGuardedSection();
      fireEvent.change(screen.getByLabelText(/city_tax_category \*/i), { target: { value: '4_STAR' } });
      fireEvent.click(screen.getByText('leave page'));
      expect(router.state.location.pathname).toBe('/page');
      expect(screen.getByText('common:unsaved_subject_hotel_category')).toBeInTheDocument();
    });

    it('does not ask once the category has been recorded', async () => {
      vi.mocked(stayService.recordHotelCategory).mockResolvedValue(CATEGORY_ENTRY);
      const { router } = await renderGuardedSection();
      fireEvent.change(screen.getByLabelText(/city_tax_category \*/i), { target: { value: '4_STAR' } });
      fireEvent.change(screen.getByLabelText(/city_tax_valid_from \*/i), { target: { value: '2026-06-01' } });
      fireEvent.click(screen.getByText('city_tax_add_category'));
      await waitFor(() => expect(stayService.recordHotelCategory).toHaveBeenCalled());
      await waitFor(() => expect(screen.getByLabelText(/city_tax_category \*/i)).toHaveValue(''));

      fireEvent.click(screen.getByText('leave page'));
      expect(router.state.location.pathname).toBe('/elsewhere');
    });
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
