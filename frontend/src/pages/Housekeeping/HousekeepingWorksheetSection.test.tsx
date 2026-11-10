import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { renderWithQuery } from '../../test-utils';
import { HousekeepingWorksheetSection } from './HousekeepingWorksheetSection';
import { housekeepingService } from '../../services';

// `t` keeps one identity across renders, like the real one.
const i18n = vi.hoisted(() => {
  const t = (key: string, options?: Record<string, unknown>) =>
    options ? `${key} ${JSON.stringify(options)}` : key;
  const value = { t, i18n: { language: 'en' } };
  return { useTranslation: () => value };
});

vi.mock('react-i18next', () => ({
  useTranslation: i18n.useTranslation,
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

const onRoomSelect = vi.fn();

// A call, not an inline array, so the router gets one entry without tripping react-perf.
const entriesFor = (url: string) => [url];

const Where = () => {
  const { search } = useLocation();
  return <output data-testid="search">{search}</output>;
};

const render = (url = '/housekeeping') =>
  renderWithQuery(
    <MemoryRouter initialEntries={entriesFor(url)}>
      <HousekeepingWorksheetSection onRoomSelect={onRoomSelect} />
      <Where />
    </MemoryRouter>,
  );

const ROW = {
  roomNumber: '204', roomTypeName: 'Standard', taskType: 'DEPARTURE' as const, currentStatus: 'DIRTY' as const,
  pax: 2, expectedDepartureDate: null, turnover: true, departureDateUnknown: false,
};

describe('HousekeepingWorksheetSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(housekeepingService.getBusinessDate).mockResolvedValue(BUSINESS_DATE);
    vi.mocked(housekeepingService.getWorksheet).mockResolvedValue(WORKSHEET_PROVISIONAL);
  });

  it('defaults the date picker to the server-resolved business date, not a client-computed today', async () => {
    render();

    const dateInput = await screen.findByLabelText('worksheet_date_label') as HTMLInputElement;
    await waitFor(() => expect(dateInput.value).toBe(BUSINESS_DATE.businessDate));
  });

  it('fetches the worksheet for the resolved business date and shows the summary counts', async () => {
    render();

    await waitFor(() =>
      expect(housekeepingService.getWorksheet).toHaveBeenCalledWith(BUSINESS_DATE.businessDate));
    expect(await screen.findByText('worksheet_provisional')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('shows a definitive badge once the worksheet is no longer provisional', async () => {
    vi.mocked(housekeepingService.getWorksheet).mockResolvedValue({ ...WORKSHEET_PROVISIONAL, provisional: false });
    render();

    expect(await screen.findByText('worksheet_definitive')).toBeInTheDocument();
    expect(screen.queryByText('worksheet_provisional')).not.toBeInTheDocument();
  });

  it('does not show a date-mismatch warning while the picker still shows the business date', async () => {
    render();

    await screen.findByText('worksheet_provisional');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('warns when the user picks a date other than the current business date', async () => {
    render();
    const dateInput = await screen.findByLabelText('worksheet_date_label');

    fireEvent.change(dateInput, { target: { value: '2026-10-06' } });

    expect(await screen.findByRole('alert')).toHaveTextContent('worksheet_date_mismatch_warning');
    expect(housekeepingService.getWorksheet).toHaveBeenCalledWith('2026-10-06');
  });

  it('downloads the worksheet PDF for the currently selected date', async () => {
    render();
    await screen.findByText('worksheet_provisional');

    fireEvent.click(screen.getByText('download_worksheet_pdf'));

    expect(housekeepingService.downloadWorksheetPdf).toHaveBeenCalledWith(BUSINESS_DATE.businessDate);
  });

  it('shows an error message when the worksheet fails to load', async () => {
    vi.mocked(housekeepingService.getWorksheet).mockRejectedValue(new Error('network down'));
    render();

    expect(await screen.findByText('failed_load_worksheet')).toBeInTheDocument();
  });

  it('starts from a valid ?date= instead of the business date', async () => {
    render('/housekeeping?date=2026-10-09');

    await waitFor(() => expect(housekeepingService.getWorksheet).toHaveBeenCalledWith('2026-10-09'));
    expect((await screen.findByLabelText('worksheet_date_label') as HTMLInputElement).value).toBe('2026-10-09');
  });

  it.each(['soon', '2026-13-45', '2026-02-30'])('ignores the invalid date %s and uses the business date', async (bad) => {
    render(`/housekeeping?date=${bad}`);

    await waitFor(() =>
      expect(housekeepingService.getWorksheet).toHaveBeenCalledWith(BUSINESS_DATE.businessDate));
    expect(housekeepingService.getWorksheet).not.toHaveBeenCalledWith(bad);
  });

  it('writes the picked date to the URL, keeping other params, and removes it when cleared', async () => {
    render('/housekeeping?status=DIRTY');
    const dateInput = await screen.findByLabelText('worksheet_date_label');

    fireEvent.change(dateInput, { target: { value: '2026-10-06' } });
    expect(screen.getByTestId('search')).toHaveTextContent('?status=DIRTY&date=2026-10-06');

    fireEvent.change(dateInput, { target: { value: '' } });
    expect(screen.getByTestId('search')).toHaveTextContent(/^\?status=DIRTY$/);
  });

  it('offers a way back to the business date when another date is shown', async () => {
    render('/housekeeping?date=2026-10-09');
    fireEvent.click(await screen.findByRole('button', { name: 'worksheet_back_to_current_date' }));

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();
    await waitFor(() => expect(
      (screen.getByLabelText('worksheet_date_label') as HTMLInputElement).value).toBe(BUSINESS_DATE.businessDate));
  });

  it('lists the rooms grouped by task, with a single turnover chip and no guest name', async () => {
    vi.mocked(housekeepingService.getWorksheet).mockResolvedValue({
      ...WORKSHEET_PROVISIONAL,
      rows: [ROW, { ...ROW, roomNumber: '205', turnover: false }],
      summary: { DEPARTURE: 2 },
    });
    render();

    fireEvent.click(await screen.findByRole('button', { name: /worksheet_rows_show/ }));
    const group = await screen.findByRole('region', { name: /worksheet_summary_departure/ });
    expect(group).toHaveTextContent('204');
    expect(group).toHaveTextContent('205');
    expect(screen.getAllByText('worksheet_turnover')).toHaveLength(1);
    expect(screen.getAllByText(/worksheet_row_pax/)).toHaveLength(2);
  });

  it('reports the room number that was clicked', async () => {
    vi.mocked(housekeepingService.getWorksheet).mockResolvedValue({ ...WORKSHEET_PROVISIONAL, rows: [ROW] });
    render();

    fireEvent.click(await screen.findByRole('button', { name: /worksheet_rows_show/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'worksheet_go_to_room {"number":"204"}' }));

    expect(onRoomSelect).toHaveBeenCalledWith('204');
  });

  it('keeps the room list closed until asked, and closes it again', async () => {
    vi.mocked(housekeepingService.getWorksheet).mockResolvedValue({ ...WORKSHEET_PROVISIONAL, rows: [ROW, { ...ROW, roomNumber: '205' }] });
    render();

    const toggle = await screen.findByRole('button', { name: 'worksheet_rows_show {"count":2}' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('button', { name: /worksheet_go_to_room/ })).not.toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();

    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(toggle).toHaveAccessibleName('worksheet_rows_hide');
    expect(screen.getAllByRole('button', { name: /worksheet_go_to_room/ })).toHaveLength(2);

    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('button', { name: /worksheet_go_to_room/ })).not.toBeInTheDocument();
  });

  it('shows no toggle when the sheet has no rooms', async () => {
    render();
    await screen.findByText('worksheet_empty');
    expect(screen.queryByRole('button', { name: /worksheet_rows_show/ })).not.toBeInTheDocument();
  });

  it('says what to do when the date has no cleaning tasks', async () => {
    render();
    expect(await screen.findByText('worksheet_empty')).toBeInTheDocument();
  });

  it('passes axe accessibility check, with the room list closed and open', async () => {
    vi.mocked(housekeepingService.getWorksheet).mockResolvedValue({ ...WORKSHEET_PROVISIONAL, rows: [ROW] });
    const { container } = render();
    await screen.findByText('worksheet_provisional');
    expect(await axe(container)).toHaveNoViolations();

    fireEvent.click(screen.getByRole('button', { name: /worksheet_rows_show/ }));
    expect(await axe(container)).toHaveNoViolations();
  });
});
