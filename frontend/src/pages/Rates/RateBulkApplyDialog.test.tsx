import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { RateBulkApplyDialog } from './RateBulkApplyDialog';
import { rateSeasonService } from '../../services';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string, opts?: Record<string, unknown>) => (opts?.count !== undefined ? `${key}:${opts.count}` : key), i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/rateSeasonService', () => ({
  rateSeasonService: {
    bulkApplyRate: vi.fn(),
  },
}));

vi.mock('../../store/toastStore', () => ({
  useToastStore: (sel: unknown) =>
    (sel as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const ROOM_TYPES = [
  { id: 'rt1', name: 'Double' },
  { id: 'rt2', name: 'Suite' },
];
const PREFILLED_ROOM_TYPE_IDS = ['rt1'];

describe('RateBulkApplyDialog', () => {
  const onClose = vi.fn();
  const onApplied = vi.fn();

  beforeEach(() => vi.clearAllMocks());

  it('renders every room type as a checkbox', () => {
    render(<RateBulkApplyDialog roomTypes={ROOM_TYPES} onClose={onClose} onApplied={onApplied} />);
    expect(screen.getByText('Double')).toBeInTheDocument();
    expect(screen.getByText('Suite')).toBeInTheDocument();
  });

  it('pre-fills room types and dates from the selection', () => {
    render(
      <RateBulkApplyDialog
        roomTypes={ROOM_TYPES}
        initialRoomTypeIds={PREFILLED_ROOM_TYPE_IDS}
        initialStartDate="2026-08-01"
        initialEndDate="2026-08-10"
        onClose={onClose}
        onApplied={onApplied}
      />,
    );
    expect(screen.getByLabelText('Double')).toBeChecked();
    expect(screen.getByLabelText('Suite')).not.toBeChecked();
    expect(screen.getByLabelText(/rate_season_start_date/i)).toHaveValue('2026-08-01');
    expect(screen.getByLabelText(/rate_season_end_date/i)).toHaveValue('2026-08-10');
  });

  it('blocks submission with no room type selected', async () => {
    render(<RateBulkApplyDialog roomTypes={ROOM_TYPES} onClose={onClose} onApplied={onApplied} />);
    fireEvent.change(screen.getByLabelText(/rate_season_start_date/i), { target: { value: '2026-08-01' } });
    fireEvent.change(screen.getByLabelText(/rate_season_end_date/i), { target: { value: '2026-08-10' } });
    fireEvent.change(screen.getByLabelText(/rate_season_nightly_price/i), { target: { value: '150' } });
    fireEvent.submit(document.querySelector('form')!);

    expect(await screen.findByText('err_select_room_type')).toBeInTheDocument();
    expect(rateSeasonService.bulkApplyRate).not.toHaveBeenCalled();
  });

  it('calls bulkApplyRate with the selected room types and closes on success', async () => {
    vi.mocked(rateSeasonService.bulkApplyRate).mockResolvedValue([]);
    render(<RateBulkApplyDialog roomTypes={ROOM_TYPES} onClose={onClose} onApplied={onApplied} />);

    fireEvent.click(screen.getByLabelText('Double'));
    fireEvent.change(screen.getByLabelText(/rate_season_start_date/i), { target: { value: '2026-08-01' } });
    fireEvent.change(screen.getByLabelText(/rate_season_end_date/i), { target: { value: '2026-08-10' } });
    fireEvent.change(screen.getByLabelText(/rate_season_nightly_price/i), { target: { value: '150' } });
    fireEvent.submit(document.querySelector('form')!);

    await waitFor(() => expect(rateSeasonService.bulkApplyRate).toHaveBeenCalledWith(expect.objectContaining({
      roomTypeIds: ['rt1'], startDate: '2026-08-01', endDate: '2026-08-10', nightlyPrice: 150,
    })));
    expect(onApplied).toHaveBeenCalledOnce();
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('shows a friendly message on a residual 409 conflict', async () => {
    vi.mocked(rateSeasonService.bulkApplyRate).mockRejectedValue({ response: { status: 409 } });
    render(<RateBulkApplyDialog roomTypes={ROOM_TYPES} onClose={onClose} onApplied={onApplied} />);

    fireEvent.click(screen.getByLabelText('Double'));
    fireEvent.change(screen.getByLabelText(/rate_season_start_date/i), { target: { value: '2026-08-01' } });
    fireEvent.change(screen.getByLabelText(/rate_season_end_date/i), { target: { value: '2026-08-10' } });
    fireEvent.change(screen.getByLabelText(/rate_season_nightly_price/i), { target: { value: '150' } });
    fireEvent.submit(document.querySelector('form')!);

    await waitFor(() => expect(rateSeasonService.bulkApplyRate).toHaveBeenCalledOnce());
    expect(onClose).not.toHaveBeenCalled();
  });

  it('passes axe accessibility check', async () => {
    const { container } = render(<RateBulkApplyDialog roomTypes={ROOM_TYPES} onClose={onClose} onApplied={onApplied} />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
