import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
/* eslint-disable react-perf/jsx-no-new-array-as-prop -- test-only render helper, not the real perf-sensitive render path */
import { axe } from 'vitest-axe';
import { renderWithQuery } from '../../test-utils';
import { RoomList } from './RoomList';
import { inventoryService } from '../../services';

const translate = (key: string) => key;
const i18nStub = { language: 'en' };
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: translate, i18n: i18nStub }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/inventoryService', () => ({
  inventoryService: {
    getAllRooms: vi.fn(),
    getAllRoomTypes: vi.fn(),
    getAvailableRooms: vi.fn(),
  },
}));

vi.mock('../../store/toastStore', () => {
  const addToast = vi.fn();
  return {
    useToastStore: (sel: unknown) =>
      (sel as (s: { addToast: () => void }) => unknown)({ addToast }),
  };
});

vi.mock('./RoomFormModal', () => ({
  RoomFormModal: () => null,
}));

const ROOM_TYPE = {
  id: 'rt1', name: 'Single', maxOccupancy: 1, basePrice: 50,
  description: '', active: true, createdAt: '2026-01-01T00:00:00', updatedAt: '2026-01-01T00:00:00',
};

const ROOM = {
  id: 'r1', hotelId: 'h1', roomNumber: '101', roomType: ROOM_TYPE,
  status: 'CLEAN' as const, active: true, createdAt: '2026-01-01T00:00:00', updatedAt: '2026-01-01T00:00:00',
};

const emptyPage = { content: [], totalElements: 0, totalPages: 1, number: 0, size: 20 };
const roomPage = { content: [ROOM], totalElements: 1, totalPages: 1, number: 0, size: 20 };

const renderPage = (state?: Record<string, unknown>) => {
  const initialEntries = [{ pathname: '/rooms', state }];
  return renderWithQuery(
    <MemoryRouter initialEntries={initialEntries}>
      <RoomList />
    </MemoryRouter>,
  );
};

describe('RoomList', () => {
  beforeEach(() => vi.clearAllMocks());

  it('has no section heading of its own (the page tabs already name the list)', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue(emptyPage as never);
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    renderPage();
    await waitFor(() => expect(screen.getByText('no_rooms_found')).toBeInTheDocument());
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });

  describe('search and room type filter', () => {
    const SUITE = { ...ROOM_TYPE, id: 'rt2', name: 'Suite', maxOccupancy: 4, basePrice: 120 };
    const rooms = [
      ROOM,
      { ...ROOM, id: 'r2', roomNumber: '102' },
      { ...ROOM, id: 'r3', roomNumber: '201', roomType: SUITE },
    ];

    const renderWithRooms = async () => {
      vi.mocked(inventoryService.getAllRooms).mockResolvedValue({ ...roomPage, content: rooms } as never);
      vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE, SUITE]);
      renderPage();
      await screen.findByText('201');
    };

    it('filters rows by room number as you type', async () => {
      await renderWithRooms();
      fireEvent.change(screen.getByRole('searchbox', { name: 'rooms_search_label' }), { target: { value: '10' } });
      expect(screen.getByText('101')).toBeInTheDocument();
      expect(screen.getByText('102')).toBeInTheDocument();
      expect(screen.queryByText('201')).not.toBeInTheDocument();
    });

    it('filters rows by room type with one chip at a time and "all" restores the list', async () => {
      await renderWithRooms();
      fireEvent.click(screen.getByRole('button', { name: 'Suite' }));
      expect(screen.getByRole('button', { name: 'Suite' })).toHaveAttribute('aria-pressed', 'true');
      expect(screen.getByText('201')).toBeInTheDocument();
      expect(screen.queryByText('101')).not.toBeInTheDocument();

      fireEvent.click(screen.getByRole('button', { name: 'filter_all' }));
      expect(screen.getByText('101')).toBeInTheDocument();
      expect(screen.getByText('201')).toBeInTheDocument();
    });

    it('combines search and type, and says so when nothing matches', async () => {
      await renderWithRooms();
      fireEvent.click(screen.getByRole('button', { name: 'Suite' }));
      fireEvent.change(screen.getByRole('searchbox', { name: 'rooms_search_label' }), { target: { value: '10' } });
      expect(screen.getByText('no_rooms_match_filters')).toBeInTheDocument();
    });

    it('keeps the add-room action in the toolbar', async () => {
      await renderWithRooms();
      expect(screen.getByRole('button', { name: 'add_room' })).toBeInTheDocument();
    });
  });

  it('renders room row after data loads', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue(roomPage as never);
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    renderPage();
    await waitFor(() => expect(screen.getByText('101')).toBeInTheDocument());
  });

  it.each([
    ['CLEAN', 'bg-tertiary-container'],
    ['DIRTY', 'bg-secondary-container'],
    ['MAINTENANCE', 'bg-error-container'],
    ['OCCUPIED', 'bg-primary-container'],
  ] as const)('colours a %s room chip with %s', async (status, expectedClass) => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue({ ...roomPage, content: [{ ...ROOM, status }] } as never);
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    renderPage();
    const chip = await screen.findByText(`room_status_${status.toLowerCase()}`);
    expect(chip.closest('span')?.className).toContain(expectedClass);
  });

  it('renders empty state when no rooms', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue(emptyPage as never);
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    renderPage();
    await waitFor(() => expect(screen.getByText('no_rooms_found')).toBeInTheDocument());
  });

  it('renders error state on load failure', async () => {
    vi.mocked(inventoryService.getAllRooms).mockRejectedValue(new Error('Network error'));
    vi.mocked(inventoryService.getAllRoomTypes).mockRejectedValue(new Error('Network error'));
    renderPage();
    await waitFor(() => expect(screen.getByText('failed_load_rooms')).toBeInTheDocument());
  });

  it('add room button is disabled when no room types loaded', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue(emptyPage as never);
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([]);
    renderPage();
    await waitFor(() => {
      const btn = screen.getByText('add_room').closest('button');
      expect(btn).toBeDisabled();
    });
  });

  it('shows warning banner when no room types exist', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue(emptyPage as never);
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([]);
    renderPage();
    await waitFor(() =>
      expect(screen.getByText('error_loading_room_types (rooms_add_room_type_first)')).toBeInTheDocument()
    );
  });

  it('add room button is enabled when room types exist', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue(emptyPage as never);
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    renderPage();
    await waitFor(() => {
      const btn = screen.getByText('add_room').closest('button');
      expect(btn).not.toBeDisabled();
    });
  });

  it('opens modal on add button click', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue(emptyPage as never);
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    renderPage();
    await waitFor(() => expect(screen.getByText('add_room').closest('button')).not.toBeDisabled());
    fireEvent.click(screen.getByText('add_room'));
  });

  it('passes axe accessibility check with data', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue(roomPage as never);
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    const { container } = renderPage();
    await waitFor(() => screen.getByText('101'));
    expect(await axe(container)).toHaveNoViolations();
  });

  it('defaults to the unfiltered list and calls getAllRooms', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue(roomPage as never);
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    renderPage();
    await waitFor(() => expect(inventoryService.getAllRooms).toHaveBeenCalledTimes(1));
    expect(inventoryService.getAvailableRooms).not.toHaveBeenCalled();
  });

  it('toggling the "available today" filter switches to getAvailableRooms', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue(roomPage as never);
    vi.mocked(inventoryService.getAvailableRooms).mockResolvedValue([ROOM]);
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    renderPage();
    await waitFor(() => expect(screen.getByText('101')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'rooms_available_today_filter' }));

    await waitFor(() => expect(inventoryService.getAvailableRooms).toHaveBeenCalledTimes(1));
  });

  it('starts with the "available today" filter on when navigated from the dashboard', async () => {
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    vi.mocked(inventoryService.getAvailableRooms).mockResolvedValue([]);
    renderPage({ availableToday: true });

    await waitFor(() => expect(inventoryService.getAvailableRooms).toHaveBeenCalledTimes(1));
    expect(inventoryService.getAllRooms).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.getByText('no_rooms_available_today')).toBeInTheDocument());
  });
});
