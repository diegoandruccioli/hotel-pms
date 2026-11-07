import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent, within } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { MemoryRouter } from 'react-router-dom';
import type { ReactElement } from 'react';
import { renderWithQuery } from '../test-utils';
import { Housekeeping } from './Housekeeping';
import { inventoryService } from '../services';
import { dashboardService } from '../services';
import { housekeepingService } from '../services';
import { mockAxiosErrorWithDetail } from '../test-utils';

const stableT = (key: string, options?: { count?: number; status?: string; dirty?: number; clean?: number; maintenance?: number; occupied?: number }) => {
  if (key === 'housekeeping_status_summary') {
    return `${key}:${options?.dirty}/${options?.clean}/${options?.maintenance}/${options?.occupied}`;
  }
  if (options?.count !== undefined) return `${key} ${options.count}`;
  return key;
};
// PageHeader derives its breadcrumbs from the router location.
const render = (ui: ReactElement) => renderWithQuery(<MemoryRouter>{ui}</MemoryRouter>);

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: stableT, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/inventoryService', () => ({
  inventoryService: {
    getAllRooms: vi.fn(),
    updateRoomStatus: vi.fn(),
    updateRoomStatusBulk: vi.fn(),
  },
}));

vi.mock('../services/dashboardService', () => ({
  dashboardService: { getDaySheet: vi.fn() },
}));

vi.mock('../services/housekeepingService', () => ({
  housekeepingService: {
    getBusinessDate: vi.fn(),
    getWorksheet: vi.fn(),
    downloadWorksheetPdf: vi.fn(),
  },
}));

const mockAddToast = vi.fn();
vi.mock('../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: typeof mockAddToast }) => unknown)({ addToast: mockAddToast }),
}));

const MOCK_DAY_SHEET = {
  date: '2026-08-20',
  todayArrivals: 0,
  todayDepartures: 0,
  guestsInHouse: 0,
  currentStays: 0,
  availableRooms: 0,
  roomStatusCounts: { CLEAN: 3, DIRTY: 1, MAINTENANCE: 0, OCCUPIED: 2 },
};

describe('Housekeeping', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(dashboardService.getDaySheet).mockResolvedValue(MOCK_DAY_SHEET);
    vi.mocked(housekeepingService.getBusinessDate).mockResolvedValue(
      { businessDate: '2026-08-20', timezone: 'Europe/Rome', cutoffHour: 4 });
    // Two-digit, mutually distinct counts — this page's own room-status-count
    // assertions below use single digits (3, 1, 0); these must never collide.
    vi.mocked(housekeepingService.getWorksheet).mockResolvedValue(
      { date: '2026-08-20', generatedAt: '2026-08-20T04:00:00', provisional: false,
        hotelName: 'Test Hotel', rows: [],
        summary: { DEPARTURE: 11, STAYOVER: 12, ARRIVAL_PREP: 13, VACANT_DIRTY: 14, MAINTENANCE: 15 } });
  });

  it('should show loading spinner initially', () => {
    vi.mocked(inventoryService.getAllRooms).mockReturnValue(new Promise(() => {}));
    render(<Housekeeping />);
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should render room cards on success', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({
      content: [
        { id: '1', roomNumber: '101', type: 'Standard', status: 'CLEAN', pricePerNight: 100 },
      ],
      totalElements: 1,
    } as never);

    render(<Housekeeping />);

    await waitFor(() => {
      expect(screen.getByText('room_number')).toBeInTheDocument();
    });
  });

  it('should show empty state when no rooms', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({ content: [], totalElements: 0 } as never);
    render(<Housekeeping />);

    await waitFor(() => {
      expect(screen.getByText('no_rooms_found')).toBeInTheDocument();
    });
  });

  it('should show error on failure', async () => {
    vi.mocked(inventoryService.getAllRooms).mockRejectedValueOnce(new Error('Connection refused'));
    render(<Housekeeping />);

    await waitFor(() => {
      expect(screen.getByText('error_loading_rooms')).toBeInTheDocument();
    });
  });

  it('should render page title', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({ content: [], totalElements: 0 } as never);
    render(<Housekeeping />);

    await waitFor(() => {
      expect(screen.getByText('nav_housekeeping')).toBeInTheDocument();
    });
  });

  it('should render OCCUPIED room without action buttons or a select checkbox', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({
      content: [
        { id: '2', roomNumber: '102', type: 'Standard', status: 'OCCUPIED', pricePerNight: 100 },
      ],
      totalElements: 1,
    } as never);

    render(<Housekeeping />);

    await waitFor(() => {
      expect(screen.getAllByText('room_status_occupied').length).toBeGreaterThan(0);
    });
    expect(screen.queryByText(/→/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText('select_room')).not.toBeInTheDocument();
  });

  it('changes a room status to DIRTY and shows a success toast', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({
      content: [{ id: '1', roomNumber: '101', type: 'Standard', status: 'CLEAN', pricePerNight: 100 }],
      totalElements: 1,
    } as never);
    vi.mocked(inventoryService.updateRoomStatus).mockResolvedValueOnce(
      { id: '1', roomNumber: '101', type: 'Standard', status: 'DIRTY', pricePerNight: 100 } as never,
    );
    render(<Housekeeping />);
    await waitFor(() => expect(screen.getByText('room_number')).toBeInTheDocument());

    // Filter badge "DIRTY" (always rendered) and the room card's "→ DIRTY" action button
    // both match /room_status_dirty/ — the action button is the arrow-prefixed one.
    const dirtyButtons = screen.getAllByRole('button', { name: /room_status_dirty/i });
    const actionButton = dirtyButtons.find((b) => b.textContent?.startsWith('→'))!;
    fireEvent.click(actionButton);
    await waitFor(() => expect(inventoryService.updateRoomStatus).toHaveBeenCalledWith('1', 'DIRTY'));
    expect(mockAddToast).toHaveBeenCalledWith('room_updated', 'success');
  });

  it('shows an error toast when the status update fails', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({
      content: [{ id: '1', roomNumber: '101', type: 'Standard', status: 'CLEAN', pricePerNight: 100 }],
      totalElements: 1,
    } as never);
    vi.mocked(inventoryService.updateRoomStatus).mockRejectedValueOnce(new Error('boom'));
    render(<Housekeeping />);
    await waitFor(() => expect(screen.getByText('room_number')).toBeInTheDocument());

    const dirtyButtons = screen.getAllByRole('button', { name: /room_status_dirty/i });
    const actionButton = dirtyButtons.find((b) => b.textContent?.startsWith('→'))!;
    fireEvent.click(actionButton);
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('failed_update_room', 'error'));
  });

  it('shows the backend detail instead of the generic fallback when the status update fails', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({
      content: [{ id: '1', roomNumber: '101', type: 'Standard', status: 'CLEAN', pricePerNight: 100 }],
      totalElements: 1,
    } as never);
    vi.mocked(inventoryService.updateRoomStatus).mockRejectedValueOnce(
      mockAxiosErrorWithDetail('ROOM_STATUS_TRANSITION_NOT_ALLOWED', 409),
    );
    render(<Housekeeping />);
    await waitFor(() => expect(screen.getByText('room_number')).toBeInTheDocument());

    const dirtyButtons = screen.getAllByRole('button', { name: /room_status_dirty/i });
    const actionButton = dirtyButtons.find((b) => b.textContent?.startsWith('→'))!;
    fireEvent.click(actionButton);
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('ROOM_STATUS_TRANSITION_NOT_ALLOWED', 'error'));
  });

  it('does not render an action button for the room\'s own current status', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({
      content: [{ id: '1', roomNumber: '101', type: 'Standard', status: 'CLEAN', pricePerNight: 100 }],
      totalElements: 1,
    } as never);
    render(<Housekeeping />);
    await waitFor(() => expect(screen.getByText('room_number')).toBeInTheDocument());

    // CLEAN room only shows action buttons for DIRTY/MAINTENANCE — its own status is filtered out.
    expect(screen.queryByRole('button', { name: /^→ room_status_clean/i })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '→ room_status_dirty' })).toBeInTheDocument();
    expect(inventoryService.updateRoomStatus).not.toHaveBeenCalled();
  });

  it('marks each room card with its canonical tone (dirty = warning, maintenance = error)', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({
      content: [
        { id: '1', roomNumber: '101', type: 'Standard', status: 'DIRTY', pricePerNight: 100 },
        { id: '2', roomNumber: '201', type: 'Standard', status: 'MAINTENANCE', pricePerNight: 100 },
      ],
      totalElements: 2,
    } as never);
    render(<Housekeeping />);
    const bars = await screen.findAllByTestId('room-card-tone');
    expect(bars[0]).toHaveClass('bg-secondary');
    expect(bars[1]).toHaveClass('bg-error');
  });

  it('styles the status-change buttons with the canonical room tones (dirty = warning, maintenance = error)', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({
      content: [{ id: '1', roomNumber: '101', type: 'Standard', status: 'CLEAN', pricePerNight: 100 }],
      totalElements: 1,
    } as never);
    render(<Housekeeping />);
    const dirty = await screen.findByRole('button', { name: '→ room_status_dirty' });
    const maintenance = screen.getByRole('button', { name: '→ room_status_maintenance' });
    expect(dirty.className).toContain('border-secondary');
    expect(maintenance.className).toContain('border-error');
  });

  it('summarises the day-sheet counts in the subtitle', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue({ content: [], totalElements: 0 } as never);
    render(<Housekeeping />);
    // MOCK_DAY_SHEET: CLEAN 3, DIRTY 1, MAINTENANCE 0, OCCUPIED 2 — order is dirty/clean/maintenance/occupied.
    expect(await screen.findByText('housekeeping_status_summary:1/3/0/2')).toBeInTheDocument();
    expect(screen.queryByText('housekeeping_subtitle')).not.toBeInTheDocument();
  });

  it('keeps the static subtitle when the day-sheet is unavailable', async () => {
    vi.mocked(dashboardService.getDaySheet).mockRejectedValue(new Error('down'));
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue({ content: [], totalElements: 0 } as never);
    render(<Housekeeping />);
    expect(await screen.findByText('housekeeping_subtitle')).toBeInTheDocument();
  });

  const BOARD_ROOMS = [
    { id: '1', roomNumber: '101', roomType: { name: 'Standard', basePrice: 100 }, status: 'DIRTY' },
    { id: '2', roomNumber: '102', roomType: { name: 'Standard', basePrice: 100 }, status: 'CLEAN' },
    { id: '3', roomNumber: '103', roomType: { name: 'Standard', basePrice: 100 }, status: 'MAINTENANCE' },
    { id: '4', roomNumber: '104', roomType: { name: 'Standard', basePrice: 100 }, status: 'OCCUPIED' },
    { id: '5', roomNumber: '105', roomType: { name: 'Standard', basePrice: 100 }, status: 'DIRTY' },
  ];
  const dropData = (roomId: string) => ({ dataTransfer: { getData: () => roomId, setData: vi.fn(), dropEffect: '', effectAllowed: '' } });
  const loadBoard = async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue({ content: BOARD_ROOMS, totalElements: 5 } as never);
    render(<Housekeeping />);
    await screen.findByRole('group', { name: 'room_status_dirty' });
  };

  it('groups the rooms into one column per status with their counts', async () => {
    await loadBoard();
    const dirty = within(screen.getByRole('group', { name: 'room_status_dirty' }));
    expect(dirty.getAllByRole('heading', { level: 3 })).toHaveLength(2);
    expect(dirty.getByText('housekeeping_column_count 2')).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'room_status_clean' })).getAllByRole('heading', { level: 3 })).toHaveLength(1);
    expect(within(screen.getByRole('group', { name: 'room_status_maintenance' })).getAllByRole('heading', { level: 3 })).toHaveLength(1);
    expect(within(screen.getByRole('group', { name: 'room_status_occupied' })).getAllByRole('heading', { level: 3 })).toHaveLength(1);
  });

  it('moves a room to another column when it is dropped there', async () => {
    vi.mocked(inventoryService.updateRoomStatus).mockResolvedValue({ ...BOARD_ROOMS[0], status: 'CLEAN' } as never);
    await loadBoard();

    fireEvent.drop(screen.getByRole('group', { name: 'room_status_clean' }), dropData('1'));

    await waitFor(() => expect(inventoryService.updateRoomStatus).toHaveBeenCalledWith('1', 'CLEAN'));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith(expect.any(String), 'success'));
  });

  it('ignores a drop on the same column, on the occupied column and of an occupied room', async () => {
    await loadBoard();

    fireEvent.drop(screen.getByRole('group', { name: 'room_status_dirty' }), dropData('1'));
    fireEvent.drop(screen.getByRole('group', { name: 'room_status_occupied' }), dropData('1'));
    fireEvent.drop(screen.getByRole('group', { name: 'room_status_clean' }), dropData('4'));

    expect(inventoryService.updateRoomStatus).not.toHaveBeenCalled();
  });

  it('shows an error toast when a dropped room cannot be updated', async () => {
    vi.mocked(inventoryService.updateRoomStatus).mockRejectedValue(new Error('boom'));
    await loadBoard();

    fireEvent.drop(screen.getByRole('group', { name: 'room_status_clean' }), dropData('1'));

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('failed_update_room', 'error'));
  });

  it('ignores a second drop of a room whose update is still in flight', async () => {
    let resolve: (v: unknown) => void = () => {};
    vi.mocked(inventoryService.updateRoomStatus).mockReturnValue(new Promise((r) => { resolve = r; }) as never);
    await loadBoard();

    fireEvent.drop(screen.getByRole('group', { name: 'room_status_clean' }), dropData('1'));
    await waitFor(() => expect(inventoryService.updateRoomStatus).toHaveBeenCalledTimes(1));
    fireEvent.drop(screen.getByRole('group', { name: 'room_status_maintenance' }), dropData('1'));

    expect(inventoryService.updateRoomStatus).toHaveBeenCalledTimes(1);
    resolve({ ...BOARD_ROOMS[0], status: 'CLEAN' });
    await waitFor(() => expect(mockAddToast).toHaveBeenCalled());
  });

  it('puts keyboard focus back on the moved card after a button move', async () => {
    vi.mocked(inventoryService.updateRoomStatus).mockResolvedValue({ ...BOARD_ROOMS[0], status: 'CLEAN' } as never);
    await loadBoard();

    const dirty = within(screen.getByRole('group', { name: 'room_status_dirty' }));
    fireEvent.click(dirty.getAllByRole('button', { name: '→ room_status_clean' })[0]);

    await waitFor(() => expect(within(screen.getByRole('group', { name: 'room_status_clean' }))
      .getAllByRole('heading', { level: 3 }).some((h) => h === document.activeElement)).toBe(true));
  });

  it('flags the dirty rooms of a turnover or arrival prep with a priority chip', async () => {
    vi.mocked(housekeepingService.getWorksheet).mockResolvedValue(
      { date: '2026-08-20', generatedAt: '2026-08-20T04:00:00', provisional: false, hotelName: null, summary: {},
        rows: [
          { roomNumber: '101', roomTypeName: 'Standard', taskType: 'DEPARTURE', currentStatus: 'DIRTY', pax: 1, expectedDepartureDate: null, turnover: true, departureDateUnknown: false },
          { roomNumber: '102', roomTypeName: 'Standard', taskType: 'ARRIVAL_PREP', currentStatus: 'CLEAN', pax: 0, expectedDepartureDate: null, turnover: false, departureDateUnknown: false },
        ] } as never);
    await loadBoard();

    const dirty = within(screen.getByRole('group', { name: 'room_status_dirty' }));
    await waitFor(() => expect(dirty.getAllByText('housekeeping_priority')).toHaveLength(1));
    // 102 is clean: priority only applies to rooms still to be cleaned.
    expect(within(screen.getByRole('group', { name: 'room_status_clean' })).queryByText('housekeeping_priority')).not.toBeInTheDocument();
  });

  it('shows no priority chips when the worksheet is unavailable', async () => {
    vi.mocked(housekeepingService.getWorksheet).mockRejectedValue(new Error('down'));
    await loadBoard();
    expect(screen.queryByText('housekeeping_priority')).not.toBeInTheDocument();
  });

  it('retries loading rooms when the try_again button is clicked after a failure', async () => {
    vi.mocked(inventoryService.getAllRooms).mockRejectedValueOnce(new Error('Connection refused'));
    render(<Housekeeping />);
    await waitFor(() => expect(screen.getByText('error_loading_rooms')).toBeInTheDocument());

    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({ content: [], totalElements: 0 } as never);
    fireEvent.click(screen.getByText('try_again'));

    await waitFor(() => expect(screen.getByText('no_rooms_found')).toBeInTheDocument());
  });

  it('shows a non-Error rejection via the fallback failed_load_rooms message', async () => {
    vi.mocked(inventoryService.getAllRooms).mockRejectedValueOnce('not an Error instance');
    render(<Housekeeping />);
    await waitFor(() => expect(screen.getByText('failed_load_rooms')).toBeInTheDocument());
  });

  it('selects rooms and applies a bulk status change', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({
      content: [
        { id: '1', roomNumber: '101', type: 'Standard', status: 'CLEAN', pricePerNight: 100 },
        { id: '2', roomNumber: '102', type: 'Standard', status: 'CLEAN', pricePerNight: 100 },
      ],
      totalElements: 2,
    } as never);
    vi.mocked(inventoryService.updateRoomStatusBulk).mockResolvedValueOnce([
      { id: '1', roomNumber: '101', type: 'Standard', status: 'DIRTY', pricePerNight: 100 },
      { id: '2', roomNumber: '102', type: 'Standard', status: 'DIRTY', pricePerNight: 100 },
    ] as never);
    render(<Housekeeping />);
    await waitFor(() => expect(screen.getAllByText('room_number')).toHaveLength(2));

    fireEvent.click(screen.getByLabelText('select_all_visible'));
    await waitFor(() => expect(screen.getByText(/n_rooms_selected 2/)).toBeInTheDocument());

    const bulkDirtyButton = screen
      .getAllByRole('button', { name: 'room_status_dirty' })
      .find((b) => !b.textContent?.startsWith('→'))!;
    fireEvent.click(bulkDirtyButton);

    await waitFor(() =>
      expect(inventoryService.updateRoomStatusBulk).toHaveBeenCalledWith(['1', '2'], 'DIRTY'),
    );
    expect(mockAddToast).toHaveBeenCalledWith('rooms_updated 2', 'success');
  });

  it('does not render a select-all control when every visible room is OCCUPIED', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({
      content: [{ id: '2', roomNumber: '102', type: 'Standard', status: 'OCCUPIED', pricePerNight: 100 }],
      totalElements: 1,
    } as never);
    render(<Housekeeping />);
    await waitFor(() => expect(screen.getAllByText('room_status_occupied').length).toBeGreaterThan(0));
    expect(screen.queryByLabelText('select_all_visible')).not.toBeInTheDocument();
  });

  it('should have no accessibility violations', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({ content: [], totalElements: 0 } as never);
    const { container } = render(<Housekeeping />);
    await waitFor(() => expect(screen.getByText('no_rooms_found')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
