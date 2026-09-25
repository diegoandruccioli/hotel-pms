import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { renderWithQuery as render } from '../../test-utils';
import { HousekeepingWorksheetSection } from './HousekeepingWorksheetSection';
import { housekeepingService } from '../../services';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options ? `${key} ${JSON.stringify(options)}` : key,
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/housekeepingService', () => ({
  housekeepingService: {
    getBusinessDate: vi.fn(),
    getWorksheet: vi.fn(),
    downloadWorksheetPdf: vi.fn(),
  },
}));

const BUSINESS_DATE = { businessDate: '2026-10-05', timezone: 'Europe/Rome', cutoffHour: 4 };

const WORKSHEET_PROVISIONAL = {
  date: '2026-10-05',
  generatedAt: '2026-10-05T02:00:00',
  provisional: true,
  hotelName: 'Test Hotel',
  rows: [],
  summary: { DEPARTURE: 2, STAYOVER: 1 },
};

describe('HousekeepingWorksheetSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(housekeepingService.getBusinessDate).mockResolvedValue(BUSINESS_DATE);
    vi.mocked(housekeepingService.getWorksheet).mockResolvedValue(WORKSHEET_PROVISIONAL);
  });

  it('defaults the date picker to the server-resolved business date, not a client-computed today', async () => {
    render(<HousekeepingWorksheetSection />);

    const dateInput = await screen.findByLabelText('worksheet_date_label') as HTMLInputElement;
    await waitFor(() => expect(dateInput.value).toBe(BUSINESS_DATE.businessDate));
  });

  it('fetches the worksheet for the resolved business date and shows the summary counts', async () => {
    render(<HousekeepingWorksheetSection />);

    await waitFor(() =>
      expect(housekeepingService.getWorksheet).toHaveBeenCalledWith(BUSINESS_DATE.businessDate));
    expect(await screen.findByText('worksheet_provisional')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('shows a definitive badge once the worksheet is no longer provisional', async () => {
    vi.mocked(housekeepingService.getWorksheet).mockResolvedValue({ ...WORKSHEET_PROVISIONAL, provisional: false });
    render(<HousekeepingWorksheetSection />);

    expect(await screen.findByText('worksheet_definitive')).toBeInTheDocument();
    expect(screen.queryByText('worksheet_provisional')).not.toBeInTheDocument();
  });

  it('does not show a date-mismatch warning while the picker still shows the business date', async () => {
    render(<HousekeepingWorksheetSection />);

    await screen.findByText('worksheet_provisional');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('warns when the user picks a date other than the current business date', async () => {
    render(<HousekeepingWorksheetSection />);
    const dateInput = await screen.findByLabelText('worksheet_date_label');

    fireEvent.change(dateInput, { target: { value: '2026-10-06' } });

    expect(await screen.findByRole('alert')).toHaveTextContent('worksheet_date_mismatch_warning');
    expect(housekeepingService.getWorksheet).toHaveBeenCalledWith('2026-10-06');
  });

  it('downloads the worksheet PDF for the currently selected date', async () => {
    render(<HousekeepingWorksheetSection />);
    await screen.findByText('worksheet_provisional');

    fireEvent.click(screen.getByText('download_worksheet_pdf'));

    expect(housekeepingService.downloadWorksheetPdf).toHaveBeenCalledWith(BUSINESS_DATE.businessDate);
  });

  it('shows an error message when the worksheet fails to load', async () => {
    vi.mocked(housekeepingService.getWorksheet).mockRejectedValue(new Error('network down'));
    render(<HousekeepingWorksheetSection />);

    expect(await screen.findByText('failed_load_worksheet')).toBeInTheDocument();
  });

  it('passes axe accessibility check', async () => {
    const { container } = render(<HousekeepingWorksheetSection />);
    await screen.findByText('worksheet_provisional');
    expect(await axe(container)).toHaveNoViolations();
  });
});
