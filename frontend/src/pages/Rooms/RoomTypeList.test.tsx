import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { RoomTypeList } from './RoomTypeList';
import { inventoryService } from '../../services/inventoryService';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
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

const ROOM_TYPE = {
  id: 'rt1', name: 'Single', maxOccupancy: 1, basePrice: 50,
  description: 'A single room', active: true,
  createdAt: '2026-01-01T00:00:00', updatedAt: '2026-01-01T00:00:00',
};

describe('RoomTypeList', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders heading', async () => {
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    render(<RoomTypeList />);
    await waitFor(() => expect(screen.getByText('tab_room_types')).toBeInTheDocument());
  });

  it('renders room type row after data loads', async () => {
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    render(<RoomTypeList />);
    await waitFor(() => expect(screen.getByText('Single')).toBeInTheDocument());
    expect(screen.getByText('€ 50.00')).toBeInTheDocument();
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

  it('passes axe accessibility check', async () => {
    vi.mocked(inventoryService.getAllRoomTypes).mockResolvedValue([ROOM_TYPE]);
    const { container } = render(<RoomTypeList />);
    await waitFor(() => screen.getByText('Single'));
    expect(await axe(container)).toHaveNoViolations();
  });
});
