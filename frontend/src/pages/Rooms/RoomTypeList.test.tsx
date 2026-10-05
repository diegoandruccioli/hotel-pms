import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { renderWithQuery as render } from '../../test-utils';
import { RoomTypeList } from './RoomTypeList';
import { inventoryService } from '../../services';

let mockLanguage = 'en';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: mockLanguage } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/inventoryService', () => ({
  inventoryService: { getAllRoomTypes: vi.fn() },
}));

vi.mock('../../store/toastStore', () => ({
  useToastStore: (sel: unknown) =>
    (sel as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

vi.mock('./RoomTypeFormModal', () => ({
  RoomTypeFormModal: () => null,
}));

vi.mock('./RateSeasonManagerModal', () => ({
  RateSeasonManagerModal: ({ roomType }: { roomType: { name: string } }) => (
    <div data-testid="rate-season-modal">{roomType.name}</div>
  ),
}));

const ROOM_TYPE = {
  id: 'rt1', name: 'Single', maxOccupancy: 1, basePrice: 50,
  description: 'A single room', active: true,
  createdAt: '2026-01-01T00:00:00', updatedAt: '2026-01-01T00:00:00',
};

describe('RoomTypeList', () => {
  beforeEach(() => vi.clearAllMocks());

  it('has no heading of its own, only the add action', async () => {
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    render(<RoomTypeList />);
    await waitFor(() => expect(screen.getByText('add_room_type')).toBeInTheDocument());
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });

  it('renders room type row after data loads', async () => {
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    render(<RoomTypeList />);
    await waitFor(() => expect(screen.getByText('Single')).toBeInTheDocument());
    expect(screen.getByText('€50.00')).toBeInTheDocument();
  });

  it('formats the base price in Italian locale', async () => {
    mockLanguage = 'it';
    try {
      vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
      render(<RoomTypeList />);
      await waitFor(() => expect(screen.getByText('Single')).toBeInTheDocument());
      expect(screen.getByText(/^50,00\s€$/)).toBeInTheDocument();
    } finally {
      mockLanguage = 'en';
    }
  });

  it('renders empty state when no room types', async () => {
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([]);
    render(<RoomTypeList />);
    await waitFor(() => expect(screen.getByText('no_rooms_found')).toBeInTheDocument());
  });

  it('renders error state on load failure', async () => {
    vi.mocked(inventoryService.getAllRoomTypes).mockRejectedValue(new Error('fail'));
    render(<RoomTypeList />);
    await waitFor(() => expect(screen.getByText('error_loading_room_types')).toBeInTheDocument());
  });

  it('add button opens modal', async () => {
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([]);
    render(<RoomTypeList />);
    await waitFor(() => expect(screen.getByText('add_room_type')).toBeInTheDocument());
    fireEvent.click(screen.getByText('add_room_type'));
  });

  it('edit button opens modal for existing type', async () => {
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    render(<RoomTypeList />);
    await waitFor(() => expect(screen.getByText('edit')).toBeInTheDocument());
    fireEvent.click(screen.getByText('edit'));
  });

  it('rate_seasons action opens the seasons modal for the row', async () => {
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    render(<RoomTypeList />);
    await waitFor(() => expect(screen.getByText('rate_seasons')).toBeInTheDocument());
    fireEvent.click(screen.getByText('rate_seasons'));
    expect(await screen.findByTestId('rate-season-modal')).toHaveTextContent('Single');
  });

  it('passes axe accessibility check', async () => {
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    const { container } = render(<RoomTypeList />);
    await waitFor(() => screen.getByText('Single'));
    expect(await axe(container)).toHaveNoViolations();
  });
});
