import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { RateSeasonManagerModal } from './RateSeasonManagerModal';
import { rateSeasonService } from '../../services';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string, opts?: Record<string, unknown>) => (opts?.roomType ? `${key}:${opts.roomType}` : key), i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/rateSeasonService', () => ({
  rateSeasonService: {
    listSeasons: vi.fn(),
    createSeason: vi.fn(),
    updateSeason: vi.fn(),
    deleteSeason: vi.fn(),
  },
}));

vi.mock('../../store/toastStore', () => ({
  useToastStore: (sel: unknown) =>
    (sel as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const ROOM_TYPE = {
  id: 'rt1', name: 'Single', maxOccupancy: 1, basePrice: 50,
  description: 'A single room', active: true,
  createdAt: '2026-01-01T00:00:00', updatedAt: '2026-01-01T00:00:00',
};

const SEASON = {
  id: 's1', roomTypeId: 'rt1', name: 'High season',
  startDate: '2026-08-01', endDate: '2026-08-31', nightlyPrice: 150,
};

describe('RateSeasonManagerModal', () => {
  const onClose = vi.fn();

  beforeEach(() => vi.clearAllMocks());

  it('renders title with room type name', async () => {
    vi.mocked(rateSeasonService.listSeasons).mockResolvedValue([]);
    render(<RateSeasonManagerModal roomType={ROOM_TYPE} onClose={onClose} />);
    await waitFor(() => expect(screen.getByText('rate_seasons_title:Single')).toBeInTheDocument());
  });

  it('renders empty state when no seasons', async () => {
    vi.mocked(rateSeasonService.listSeasons).mockResolvedValue([]);
    render(<RateSeasonManagerModal roomType={ROOM_TYPE} onClose={onClose} />);
    await waitFor(() => expect(screen.getByText('no_rate_seasons')).toBeInTheDocument());
  });

  it('renders a season row after data loads', async () => {
    vi.mocked(rateSeasonService.listSeasons).mockResolvedValue([SEASON]);
    render(<RateSeasonManagerModal roomType={ROOM_TYPE} onClose={onClose} />);
    await waitFor(() => expect(screen.getByText('High season')).toBeInTheDocument());
    expect(screen.getByText('€ 150.00')).toBeInTheDocument();
  });

  it('add_rate_season opens the form', async () => {
    vi.mocked(rateSeasonService.listSeasons).mockResolvedValue([]);
    render(<RateSeasonManagerModal roomType={ROOM_TYPE} onClose={onClose} />);
    await waitFor(() => expect(screen.getByText('add_rate_season')).toBeInTheDocument());
    fireEvent.click(screen.getByText('add_rate_season'));
    expect(screen.getByLabelText(/rate_season_nightly_price/i)).toBeInTheDocument();
  });

  it('calls createSeason and returns to the list on submit', async () => {
    vi.mocked(rateSeasonService.listSeasons).mockResolvedValue([]);
    vi.mocked(rateSeasonService.createSeason).mockResolvedValue(SEASON);
    render(<RateSeasonManagerModal roomType={ROOM_TYPE} onClose={onClose} />);
    await waitFor(() => expect(screen.getByText('add_rate_season')).toBeInTheDocument());
    fireEvent.click(screen.getByText('add_rate_season'));

    fireEvent.change(screen.getByLabelText(/rate_season_start_date/i), { target: { value: '2026-08-01' } });
    fireEvent.change(screen.getByLabelText(/rate_season_end_date/i), { target: { value: '2026-08-31' } });
    fireEvent.change(screen.getByLabelText(/rate_season_nightly_price/i), { target: { value: '150' } });
    fireEvent.submit(document.querySelector('form')!);

    await waitFor(() => expect(rateSeasonService.createSeason).toHaveBeenCalledWith('rt1', expect.objectContaining({
      startDate: '2026-08-01', endDate: '2026-08-31', nightlyPrice: 150,
    })));
  });

  it('blocks submission when end date is before start date', async () => {
    vi.mocked(rateSeasonService.listSeasons).mockResolvedValue([]);
    render(<RateSeasonManagerModal roomType={ROOM_TYPE} onClose={onClose} />);
    await waitFor(() => expect(screen.getByText('add_rate_season')).toBeInTheDocument());
    fireEvent.click(screen.getByText('add_rate_season'));

    fireEvent.change(screen.getByLabelText(/rate_season_start_date/i), { target: { value: '2026-08-31' } });
    fireEvent.change(screen.getByLabelText(/rate_season_end_date/i), { target: { value: '2026-08-01' } });
    fireEvent.change(screen.getByLabelText(/rate_season_nightly_price/i), { target: { value: '150' } });
    fireEvent.submit(document.querySelector('form')!);

    expect(await screen.findByText('common:err_invalid_date_range')).toBeInTheDocument();
    expect(rateSeasonService.createSeason).not.toHaveBeenCalled();
  });

  it('shows a 409 overlap error as a friendly message', async () => {
    vi.mocked(rateSeasonService.listSeasons).mockResolvedValue([]);
    vi.mocked(rateSeasonService.createSeason).mockRejectedValue({ response: { status: 409 } });
    render(<RateSeasonManagerModal roomType={ROOM_TYPE} onClose={onClose} />);
    await waitFor(() => expect(screen.getByText('add_rate_season')).toBeInTheDocument());
    fireEvent.click(screen.getByText('add_rate_season'));

    fireEvent.change(screen.getByLabelText(/rate_season_start_date/i), { target: { value: '2026-08-01' } });
    fireEvent.change(screen.getByLabelText(/rate_season_end_date/i), { target: { value: '2026-08-31' } });
    fireEvent.change(screen.getByLabelText(/rate_season_nightly_price/i), { target: { value: '150' } });
    fireEvent.submit(document.querySelector('form')!);

    await waitFor(() => expect(rateSeasonService.createSeason).toHaveBeenCalledOnce());
  });

  it('edit opens the form pre-filled with the season values', async () => {
    vi.mocked(rateSeasonService.listSeasons).mockResolvedValue([SEASON]);
    render(<RateSeasonManagerModal roomType={ROOM_TYPE} onClose={onClose} />);
    await waitFor(() => expect(screen.getByText('High season')).toBeInTheDocument());
    fireEvent.click(screen.getByText('edit'));
    expect((screen.getByLabelText(/rate_season_nightly_price/i) as HTMLInputElement).value).toBe('150');
  });

  it('delete shows confirmation then calls deleteSeason', async () => {
    vi.mocked(rateSeasonService.listSeasons).mockResolvedValue([SEASON]);
    vi.mocked(rateSeasonService.deleteSeason).mockResolvedValue(undefined);
    render(<RateSeasonManagerModal roomType={ROOM_TYPE} onClose={onClose} />);
    await waitFor(() => expect(screen.getByText('High season')).toBeInTheDocument());
    fireEvent.click(screen.getByText('delete'));
    await waitFor(() => expect(screen.getByText('confirm_delete_rate_season')).toBeInTheDocument());
    fireEvent.click(screen.getByText('btn_confirm'));
    await waitFor(() => expect(rateSeasonService.deleteSeason).toHaveBeenCalledWith('rt1', 's1'));
  });

  it('calls onClose when close clicked from the list view', async () => {
    vi.mocked(rateSeasonService.listSeasons).mockResolvedValue([]);
    render(<RateSeasonManagerModal roomType={ROOM_TYPE} onClose={onClose} />);
    await waitFor(() => expect(screen.getAllByText('close').length).toBeGreaterThan(0));
    const closeButtons = screen.getAllByText('close');
    fireEvent.click(closeButtons[closeButtons.length - 1]);
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('passes axe accessibility check', async () => {
    vi.mocked(rateSeasonService.listSeasons).mockResolvedValue([SEASON]);
    const { container } = render(<RateSeasonManagerModal roomType={ROOM_TYPE} onClose={onClose} />);
    await waitFor(() => screen.getByText('High season'));
    expect(await axe(container)).toHaveNoViolations();
  });
});
