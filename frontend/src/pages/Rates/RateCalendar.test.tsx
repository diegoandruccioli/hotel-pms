import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { RateCalendar } from './RateCalendar';
import { rateSeasonService } from '../../services';

// `t` must be a stable reference across renders (as the real react-i18next hook
// returns) — RateCalendar's data-fetch effect depends on it, and a fresh `t` per
// call would re-trigger the fetch on every render, racing the mocked service.
const mockT = (key: string, opts?: Record<string, unknown>) =>
  (opts?.count !== undefined ? `${key}:${opts.count}` : key);
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: mockT, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/rateSeasonService', () => ({
  rateSeasonService: {
    getRateCalendar: vi.fn(),
    bulkApplyRate: vi.fn(),
  },
}));

vi.mock('../../store/toastStore', () => ({
  useToastStore: (sel: unknown) =>
    (sel as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

let mockRole: string | undefined = 'ADMIN';
vi.mock('../../store/authStore', () => ({
  useAuthStore: (selector: (s: { user: { role: string | undefined } | null }) => unknown) =>
    selector({ user: mockRole ? { role: mockRole } : null }),
}));

vi.mock('./RateBulkApplyDialog', () => ({
  RateBulkApplyDialog: ({ roomTypes, onClose }: { roomTypes: { id: string }[]; onClose: () => void }) => (
    <div data-testid="bulk-apply-dialog">
      <span>{roomTypes.length}</span>
      <button type="button" onClick={onClose}>close-dialog</button>
    </div>
  ),
}));

const CALENDAR = {
  rows: [
    {
      roomTypeId: 'rt1',
      roomTypeName: 'Double',
      basePrice: 90,
      days: [
        { date: '2026-08-01', price: 90, rateSeasonId: null, seasonName: null },
        { date: '2026-08-02', price: 120, rateSeasonId: 's1', seasonName: 'High season' },
      ],
    },
  ],
};

describe('RateCalendar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRole = 'ADMIN';
  });

  it('shows a loading spinner then renders the grid', async () => {
    vi.mocked(rateSeasonService.getRateCalendar).mockResolvedValue(CALENDAR);
    render(<RateCalendar />);
    await waitFor(() => expect(screen.getByText('Double')).toBeInTheDocument());
    expect(screen.getByText('€ 90.00')).toBeInTheDocument();
  });

  it('shows the apply-price button for ADMIN', async () => {
    vi.mocked(rateSeasonService.getRateCalendar).mockResolvedValue(CALENDAR);
    render(<RateCalendar />);
    await waitFor(() => expect(screen.getByText('Double')).toBeInTheDocument());
    expect(screen.getByText('btn_apply_price')).toBeInTheDocument();
  });

  it('hides the apply-price button for RECEPTIONIST', async () => {
    mockRole = 'RECEPTIONIST';
    vi.mocked(rateSeasonService.getRateCalendar).mockResolvedValue(CALENDAR);
    render(<RateCalendar />);
    await waitFor(() => expect(screen.getByText('Double')).toBeInTheDocument());
    expect(screen.queryByText('btn_apply_price')).not.toBeInTheDocument();
  });

  it('selecting a cell via keyboard shows the selection summary', async () => {
    vi.mocked(rateSeasonService.getRateCalendar).mockResolvedValue(CALENDAR);
    render(<RateCalendar />);
    await waitFor(() => expect(screen.getByText('Double')).toBeInTheDocument());

    fireEvent.click(screen.getByLabelText(/Double.*90\.00/));
    expect(screen.getByText('selection_summary:1')).toBeInTheDocument();
  });

  it('shift-click extends the selection across cells', async () => {
    vi.mocked(rateSeasonService.getRateCalendar).mockResolvedValue(CALENDAR);
    render(<RateCalendar />);
    await waitFor(() => expect(screen.getByText('Double')).toBeInTheDocument());

    fireEvent.click(screen.getByLabelText(/Double.*90\.00/));
    fireEvent.click(screen.getByLabelText(/Double.*120\.00/), { shiftKey: true });
    expect(screen.getByText('selection_summary:2')).toBeInTheDocument();
  });

  it('clicking the toolbar apply-price button opens the dialog', async () => {
    vi.mocked(rateSeasonService.getRateCalendar).mockResolvedValue(CALENDAR);
    render(<RateCalendar />);
    await waitFor(() => expect(screen.getByText('Double')).toBeInTheDocument());

    fireEvent.click(screen.getByText('btn_apply_price'));
    expect(screen.getByTestId('bulk-apply-dialog')).toBeInTheDocument();
  });

  it('shows an error state with retry on load failure', async () => {
    vi.mocked(rateSeasonService.getRateCalendar).mockRejectedValueOnce({ response: {} });
    vi.mocked(rateSeasonService.getRateCalendar).mockResolvedValueOnce(CALENDAR);
    render(<RateCalendar />);
    await waitFor(() => expect(screen.getAllByText('error_loading_rate_calendar').length).toBeGreaterThan(0));

    fireEvent.click(screen.getByText('try_again'));
    await waitFor(() => expect(screen.getByText('Double')).toBeInTheDocument());
  });

  it('renders the legend for a season present in the visible range', async () => {
    vi.mocked(rateSeasonService.getRateCalendar).mockResolvedValue(CALENDAR);
    render(<RateCalendar />);
    await waitFor(() => expect(screen.getByText('High season')).toBeInTheDocument());
    expect(screen.getByText('rate_calendar_legend_base_price')).toBeInTheDocument();
  });

  it('passes axe accessibility check', async () => {
    vi.mocked(rateSeasonService.getRateCalendar).mockResolvedValue(CALENDAR);
    const { container } = render(<RateCalendar />);
    await waitFor(() => screen.getByText('Double'));
    expect(await axe(container)).toHaveNoViolations();
  });
});
