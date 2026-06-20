import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { Housekeeping } from './Housekeeping';
import { inventoryService } from '../services/inventoryService';

// `t` must be a module-level stable reference: Housekeeping's loadRooms useCallback
// depends on `t`, and that callback is the sole effect dependency triggering the
// initial fetch. An inline arrow recreated on every useTranslation() call would give
// `t` (and loadRooms) a new identity every render, silently re-firing the fetch after
// every interaction in this file and exhausting the mockResolvedValueOnce queue.
const stableT = (key: string) => key;
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: stableT, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/inventoryService', () => ({
  inventoryService: { getAllRooms: vi.fn(), updateRoomStatus: vi.fn() },
}));

const mockAddToast = vi.fn();
vi.mock('../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: typeof mockAddToast }) => unknown)({ addToast: mockAddToast }),
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

  it('should render OCCUPIED room without action buttons', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({
      content: [
        { id: '2', roomNumber: '102', type: 'Standard', status: 'OCCUPIED', pricePerNight: 100 },
      ],
      totalElements: 1,
    } as never);

    render(<Housekeeping />);

    await waitFor(() => {
      expect(screen.getByText('room_status_occupied')).toBeInTheDocument();
    });
    expect(screen.queryByText(/→/)).not.toBeInTheDocument();
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

  it('does not render an action button for the room\'s own current status', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({
      content: [{ id: '1', roomNumber: '101', type: 'Standard', status: 'CLEAN', pricePerNight: 100 }],
      totalElements: 1,
    } as never);
    render(<Housekeeping />);
    await waitFor(() => expect(screen.getByText('room_number')).toBeInTheDocument());

    // CLEAN room only shows action buttons for DIRTY/MAINTENANCE — its own status is
    // filtered out, even though the "CLEAN" filter badge above is always rendered.
    const cleanButtons = screen.getAllByRole('button', { name: /room_status_clean/i });
    expect(cleanButtons.some((b) => b.textContent?.startsWith('→'))).toBe(false);
    expect(inventoryService.updateRoomStatus).not.toHaveBeenCalled();
  });

  it('toggles a status filter badge on and off', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({
      content: [
        { id: '1', roomNumber: '101', type: 'Standard', status: 'CLEAN', pricePerNight: 100 },
        { id: '2', roomNumber: '102', type: 'Standard', status: 'DIRTY', pricePerNight: 100 },
      ],
      totalElements: 2,
    } as never);
    render(<Housekeeping />);
    await waitFor(() => expect(screen.getAllByText('room_number')).toHaveLength(2));

    // The filter badge's accessible name is "<count> room_status_dirty" (no arrow prefix),
    // unlike the room card's "→ room_status_dirty" action button.
    const dirtyButtons = screen.getAllByRole('button', { name: /room_status_dirty/i });
    const filterBadge = dirtyButtons.find((b) => !b.textContent?.startsWith('→'))!;

    fireEvent.click(filterBadge);
    await waitFor(() => expect(screen.getAllByText('room_number')).toHaveLength(1));

    fireEvent.click(filterBadge);
    await waitFor(() => expect(screen.getAllByText('room_number')).toHaveLength(2));
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

  it('should have no accessibility violations', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({ content: [], totalElements: 0 } as never);
    const { container } = render(<Housekeeping />);
    await waitFor(() => expect(screen.getByText('no_rooms_found')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
