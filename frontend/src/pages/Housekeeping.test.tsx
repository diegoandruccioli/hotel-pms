import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { Housekeeping } from './Housekeeping';
import { inventoryService } from '../services/inventoryService';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/inventoryService', () => ({
  inventoryService: { getAllRooms: vi.fn(), updateRoomStatus: vi.fn() },
}));

vi.mock('../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

describe('Housekeeping', () => {
  beforeEach(() => vi.clearAllMocks());

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
});
